package com.nova.framework.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP request parser
 */
public final class HTTPParser {

    private static final int MAX_HEADER_SIZE = 8192; // 8KB headers max

    /**
     * Parse HTTP request from input stream
     */
    public static HTTPRequest parse(InputStream inputStream, String clientAddress, int maxBodySize)
            throws IOException {

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        // Parse request line
        String requestLine = reader.readLine();
        if (requestLine == null || requestLine.isEmpty()) {
            throw new IOException("Empty request");
        }

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

        // Parse query parameters
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex >= 0) {
            path = fullPath.substring(0, queryIndex);
            String queryString = fullPath.substring(queryIndex + 1);
            queryParams = parseQueryString(queryString);
        }

        // Parse headers
        Map<String, String> headers = new HashMap<>();
        String line;
        int headerSize = 0;

        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            headerSize += line.length();
            if (headerSize > MAX_HEADER_SIZE) {
                throw new IOException("Headers too large");
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String name = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }

        // Parse body
        String body = "";
        String contentLength = headers.get("Content-Length");

        if (contentLength != null) {
            try {
                int length = Integer.parseInt(contentLength);

                if (length > maxBodySize) {
                    throw new RequestTooLargeException("Request body too large: " + length);
                }

                if (length > 0) {
                    char[] buffer = new char[length];
                    int read = reader.read(buffer, 0, length);
                    body = new String(buffer, 0, read);
                }
            } catch (NumberFormatException e) {
                // Invalid Content-Length, ignore
            }
        }

        return new HTTPRequest(method, path, headers, body, queryParams, Map.of(), clientAddress);
    }

    private static Map<String, String> parseQueryString(String queryString) {
        Map<String, String> params = new HashMap<>();

        if (queryString == null || queryString.isEmpty()) {
            return params;
        }

        String[] pairs = queryString.split("&");
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue; // Skip empty pairs
            }

            int eqIndex = pair.indexOf('=');
            if (eqIndex > 0) {
                try {
                    String key = URLDecoder.decode(pair.substring(0, eqIndex), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(eqIndex + 1), StandardCharsets.UTF_8);
                    params.put(key, value);
                } catch (IllegalArgumentException e) {
                    // Skip malformed URL-encoded parameters
                }
            } else if (eqIndex == -1) {
                // Parameter without value (e.g., "?flag")
                try {
                    String key = URLDecoder.decode(pair, StandardCharsets.UTF_8);
                    params.put(key, "");
                } catch (IllegalArgumentException e) {
                    // Skip malformed URL-encoded parameters
                }
            }
        }

        return params;
    }

    /**
     * Exception for request too large
     */
    public static class RequestTooLargeException extends IOException {
        public RequestTooLargeException(String message) {
            super(message);
        }
    }
}
