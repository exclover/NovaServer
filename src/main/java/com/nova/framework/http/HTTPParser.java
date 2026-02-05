package com.nova.framework.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * High-performance, Byte-level HTTP request parser.
 * Avoids BufferedReader to prevent DoS attacks and encoding issues.
 */
public final class HTTPParser {

    // RFC 7230: HTTP Headers are strictly ASCII
    private static final int MAX_HEADER_SIZE = 8192; // 8KB
    private static final byte CR = 13; // \r
    private static final byte LF = 10; // \n

    /**
     * Parse HTTP request directly from raw InputStream bytes.
     */
    public static HTTPRequest parse(InputStream inputStream, String clientAddress, int maxBodySize)
            throws IOException {

        // 1. Read Header Block (Raw Bytes)
        // Başlıkları "\r\n\r\n" görene kadar byte byte okur.
        byte[] headerBytes = readHeaderBlock(inputStream);
        
        // 2. Decode Headers (US-ASCII is standard for HTTP headers)
        // String oluştururken memory copy oluşur ama headerlar küçük olduğu için kabul edilebilir.
        String headerBlock = new String(headerBytes, StandardCharsets.US_ASCII);
        
        // Split by Lines
        // Regex kullanmak yerine manuel split daha hızlıdır ama okunabilirlik için split kullanıyoruz.
        // Limit -1, sondaki boşlukları korumak içindir.
        String[] lines = headerBlock.split("\r\n");
        
        if (lines.length == 0) {
            throw new IOException("Empty request");
        }

        // 3. Parse Request Line (First Line)
        String requestLine = lines[0];
        String[] parts = requestLine.split(" ");
        if (parts.length < 2) {
            throw new IOException("Invalid request line: " + requestLine);
        }

        HTTPMethod method = HTTPMethod.parse(parts[0]);
        if (method == null) {
            throw new IOException("Unknown method: " + parts[0]);
        }

        String fullPath = parts[1];
        String path = fullPath;
        Map<String, String> queryParams = new HashMap<>();

        // Parse Query Parameters
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex >= 0) {
            path = fullPath.substring(0, queryIndex);
            String queryString = fullPath.substring(queryIndex + 1);
            queryParams = parseQueryString(queryString);
        }

        // 4. Parse Headers
        // Case Insensitive Map (RFC Standardı)
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.isEmpty()) continue;

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }

        // 5. Parse Body (Binary Safe)
        byte[] bodyBytes = new byte[0];
        String contentLengthStr = headers.get("Content-Length");
        
        if (contentLengthStr != null) {
            try {
                int contentLength = Integer.parseInt(contentLengthStr);
                if (contentLength > maxBodySize) {
                    throw new RequestTooLargeException("Request body too large: " + contentLength + " bytes");
                }
                
                if (contentLength > 0) {
                    bodyBytes = readBodyBytes(inputStream, contentLength);
                }
            } catch (NumberFormatException e) {
                // Invalid Content-Length, treat as 0 or handle error
            }
        }

        // Body'yi string'e çevir (Sadece metin işlemleri için gerekli, dosya upload için byte[] saklanmalı)
        // Burada varsayılan olarak UTF-8 kabul ediyoruz, ancak Content-Type charset'e bakmak daha doğrudur.
        String body = new String(bodyBytes, StandardCharsets.UTF_8);

        Map<String, String> formParams = new HashMap<>();
        String contentType = headers.get("Content-Type");
        if (contentType != null && contentType.toLowerCase().contains("application/x-www-form-urlencoded")) {
            formParams = parseQueryString(body);
        }

        return new HTTPRequest(method, path, headers, body, queryParams, formParams, clientAddress);
    }

    /**
     * Reads the stream until \r\n\r\n is found or limit is reached.
     */
    private static byte[] readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(1024);
        int totalBytes = 0;
        int b;
        
        // Son 4 byte'ı takip et: \r\n\r\n (13, 10, 13, 10) arıyoruz.
        // Basit bir state machine mantığı.
        int matchState = 0; 

        while ((b = in.read()) != -1) {
            buffer.write(b);
            totalBytes++;

            if (totalBytes > MAX_HEADER_SIZE) {
                throw new RequestTooLargeException("Header size exceeded limit of " + MAX_HEADER_SIZE + " bytes");
            }

            // State Machine for \r\n\r\n detection
            if (b == CR) {
                if (matchState == 0) matchState = 1;
                else if (matchState == 2) matchState = 3;
                else matchState = 0; // Reset
            } else if (b == LF) {
                if (matchState == 1) matchState = 2;
                else if (matchState == 3) {
                    // Match found! \r\n\r\n
                    // Ancak buffer'ın sonundaki \r\n\r\n kısmını atmak isteyebiliriz veya parse ederken trimleriz.
                    // Burada buffer'ı olduğu gibi döndürüyoruz, split ederken boş satır oluşacak, onu yutarız.
                    return buffer.toByteArray();
                } else {
                    matchState = 0;
                }
            } else {
                matchState = 0;
            }
        }
        
        // Eğer döngü biterse ve \r\n\r\n bulunamazsa:
        throw new IOException("Connection closed before headers complete");
    }

    /**
     * Reads exactly N bytes safely.
     */
    private static byte[] readBodyBytes(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int totalRead = 0;
        
        while (totalRead < length) {
            int read = in.read(buffer, totalRead, length - totalRead);
            if (read == -1) {
                throw new IOException("Unexpected end of stream while reading body");
            }
            totalRead += read;
        }
        return buffer;
    }

    private static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) return params;

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) continue;
            int eqIndex = pair.indexOf('=');
            try {
                if (eqIndex > 0) {
                    String key = URLDecoder.decode(pair.substring(0, eqIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(eqIndex + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } else if (eqIndex == -1) {
                    params.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
                }
            } catch (IllegalArgumentException e) {
                // Ignore malformed
            }
        }
        return params;
    }

    public static class RequestTooLargeException extends IOException {
        public RequestTooLargeException(String message) {
            super(message);
        }
    }
}