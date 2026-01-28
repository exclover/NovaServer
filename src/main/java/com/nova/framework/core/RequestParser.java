package com.nova.framework.core;

import java.io.*;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP Request parser with enhanced security
 * Follows HTTP/1.1 specification (RFC 7230-7235)
 * 
 * @version 2.1.0 - Fixed path traversal and double encoding attacks
 */
public class RequestParser {
    
    // Security limits
    private static final int MAX_HEADER_SIZE = 8192;
    private static final int MAX_HEADERS = 100;
    private static final int MAX_URI_LENGTH = 8192;
    private static final int MAX_TOTAL_HEADER_SIZE = 32 * 1024; // 32KB
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_DECODE_ITERATIONS = 10; // Prevent infinite decode loops
    
    /**
     * Parse HTTP request from input stream
     */
    public static Request parse(InputStream stream, String clientIP, int maxBodySize) throws IOException {
        if (stream == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        if (clientIP == null || clientIP.trim().isEmpty()) {
            throw new IllegalArgumentException("Client IP cannot be null or empty");
        }
        if (maxBodySize <= 0) {
            throw new IllegalArgumentException("Max body size must be positive");
        }
        
        Request request = new Request();
        request.setClientIP(clientIP);
        
        BufferedInputStream buffered = stream instanceof BufferedInputStream 
            ? (BufferedInputStream) stream 
            : new BufferedInputStream(stream, BUFFER_SIZE);
        
        try {
            parseRequestLine(buffered, request);
            parseHeaders(buffered, request);
            parseCookies(request);
            parseBody(buffered, request, maxBodySize);
        } catch (RequestTooLargeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Request parsing failed: " + e.getMessage(), e);
        }
        
        return request;
    }
    
    /**
     * Parse HTTP request line (method, URI, version)
     */
    private static void parseRequestLine(BufferedInputStream in, Request req) throws IOException {
        String line = readLine(in);
        
        if (line == null || line.trim().isEmpty()) {
            throw new IOException("Empty request line");
        }
        
        if (line.length() > MAX_URI_LENGTH) {
            throw new IOException("Request line too long: " + line.length() + " bytes");
        }
        
        String[] parts = line.split(" ", 3);
        if (parts.length < 3) {
            throw new IOException("Invalid HTTP request line: " + line);
        }
        
        // Parse method
        String method = parts[0].toUpperCase().trim();
        if (!isValidMethod(method)) {
            throw new IOException("Invalid HTTP method: " + method);
        }
        req.setMethod(method);
        
        // Parse URI and query string
        String uriString = parts[1].trim();
        if (uriString.isEmpty()) {
            throw new IOException("Empty URI");
        }
        
        try {
            URI uri = new URI(uriString);
            String path = uri.getPath();
            
            // Normalize path with enhanced security checks
            if (path == null || path.isEmpty()) {
                path = "/";
            } else {
                path = normalizePath(path);
            }
            
            req.setPath(path);
            
            // Parse query string
            if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
                req.setRawQuery(uri.getRawQuery());
                parseQueryString(uri.getRawQuery(), req);
            }
        } catch (Exception e) {
            throw new IOException("Invalid URI: " + uriString, e);
        }
        
        // Parse HTTP version
        String version = parts[2].trim();
        if (!version.startsWith("HTTP/")) {
            throw new IOException("Invalid HTTP version: " + version);
        }
        req.setHttpVersion(version);
    }
    
    /**
     * Validate HTTP method
     */
    private static boolean isValidMethod(String method) {
        return method != null && method.matches("^[A-Z]+$") && method.length() <= 20;
    }
    
    /**
     * Parse HTTP headers with total size limit
     */
    private static void parseHeaders(BufferedInputStream in, Request req) throws IOException {
        String line;
        int count = 0;
        int totalHeaderSize = 0;
        
        while ((line = readLine(in)) != null) {
            // Empty line signals end of headers
            if (line.isEmpty()) {
                break;
            }
            
            // Check total header size (header bomb protection)
            totalHeaderSize += line.length();
            if (totalHeaderSize > MAX_TOTAL_HEADER_SIZE) {
                throw new IOException("Total headers too large (max: " + MAX_TOTAL_HEADER_SIZE + " bytes)");
            }
            
            // Check header count
            if (++count > MAX_HEADERS) {
                throw new IOException("Too many headers (max: " + MAX_HEADERS + ")");
            }
            
            // Check individual header size
            if (line.length() > MAX_HEADER_SIZE) {
                throw new IOException("Header too large (max: " + MAX_HEADER_SIZE + " bytes)");
            }
            
            // Parse header
            int colonIdx = line.indexOf(':');
            if (colonIdx <= 0) {
                continue; // Skip invalid headers
            }
            
            String key = line.substring(0, colonIdx).trim();
            String value = line.substring(colonIdx + 1).trim();
            
            // Validate header name (RFC 7230)
            if (!isValidHeaderName(key)) {
                continue;
            }
            
            if (!key.isEmpty()) {
                req.addHeader(key, value);
            }
        }
    }
    
    /**
     * Validate header name according to RFC 7230
     */
    private static boolean isValidHeaderName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        
        for (char c : name.toCharArray()) {
            if (!isTokenChar(c)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Check if character is valid in HTTP token
     */
    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z') ||
               (c >= 'A' && c <= 'Z') ||
               (c >= '0' && c <= '9') ||
               c == '!' || c == '#' || c == '$' || c == '%' || c == '&' ||
               c == '\'' || c == '*' || c == '+' || c == '-' || c == '.' ||
               c == '^' || c == '_' || c == '`' || c == '|' || c == '~';
    }
    
    /**
     * Parse cookies from Cookie header
     */
    private static void parseCookies(Request req) {
        String cookieHeader = req.header("Cookie");
        if (cookieHeader == null || cookieHeader.trim().isEmpty()) {
            return;
        }
        
        for (String pair : cookieHeader.split(";")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            
            String[] kv = trimmed.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = urlDecode(kv[1].trim());
                if (!key.isEmpty()) {
                    req.addCookie(key, value);
                }
            }
        }
    }
    
    /**
     * Parse query string
     */
    private static void parseQueryString(String query, Request req) {
        if (query == null || query.isEmpty()) {
            return;
        }
        
        for (String param : query.split("&")) {
            if (param.isEmpty()) {
                continue;
            }
            
            String[] kv = param.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length == 2 ? urlDecode(kv[1]) : "";
            
            if (!key.isEmpty()) {
                req.addQueryParam(key, value);
            }
        }
    }
    
    /**
     * Parse request body
     */
    private static void parseBody(BufferedInputStream in, Request req, int maxSize) throws IOException {
        String contentLengthStr = req.header("Content-Length");
        
        if (contentLengthStr == null || contentLengthStr.trim().isEmpty()) {
            return;
        }
        
        long contentLength;
        try {
            contentLength = Long.parseLong(contentLengthStr.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid Content-Length: " + contentLengthStr);
        }
        
        if (contentLength < 0) {
            throw new IOException("Negative Content-Length: " + contentLength);
        }
        
        if (contentLength == 0) {
            return;
        }
        
        if (contentLength > maxSize) {
            throw new RequestTooLargeException(
                String.format("Body too large: %d bytes (max: %d)", contentLength, maxSize)
            );
        }
        
        if (contentLength > Integer.MAX_VALUE) {
            throw new RequestTooLargeException("Body size exceeds maximum: " + contentLength);
        }
        
        // Read body
        ByteArrayOutputStream buffer = new ByteArrayOutputStream((int) contentLength);
        byte[] chunk = new byte[BUFFER_SIZE];
        long totalRead = 0;
        
        while (totalRead < contentLength) {
            int toRead = (int) Math.min(chunk.length, contentLength - totalRead);
            int bytesRead = in.read(chunk, 0, toRead);
            
            if (bytesRead == -1) {
                throw new IOException(
                    String.format("Unexpected end of stream (read %d of %d bytes)", 
                                totalRead, contentLength)
                );
            }
            
            buffer.write(chunk, 0, bytesRead);
            totalRead += bytesRead;
        }
        
        req.setBody(buffer.toString(StandardCharsets.UTF_8));
    }
    
    /**
     * Read a line from input stream (CRLF or LF terminated)
     */
    private static String readLine(BufferedInputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int b;
        int count = 0;
        
        while ((b = in.read()) != -1) {
            if (++count > MAX_HEADER_SIZE) {
                throw new IOException("Line too long (max: " + MAX_HEADER_SIZE + " bytes)");
            }
            
            if (b == '\r') {
                in.mark(1);
                int next = in.read();
                if (next != '\n' && next != -1) {
                    in.reset();
                }
                break;
            } else if (b == '\n') {
                break;
            } else {
                line.write(b);
            }
        }
        
        if (line.size() == 0 && b == -1) {
            return null;
        }
        
        return line.toString(StandardCharsets.UTF_8);
    }
    
    /**
     * Normalize path with enhanced security against path traversal attacks
     * FIX: Prevents double/multiple encoding bypass attacks
     */
    private static String normalizePath(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        
        // FIX: Recursively decode until stable to prevent double encoding bypass
        String decoded = recursiveDecode(path);
        
        // Security: Detect null bytes (path truncation attack)
        if (decoded.contains("\0")) {
            throw new IOException("Null byte in path - security violation");
        }
        
        // Security: Reject backslashes (Windows path injection)
        if (decoded.contains("\\")) {
            throw new IOException("Backslash in path - security violation");
        }
        
        // Security: Strict path traversal detection AFTER full decoding
        if (containsPathTraversal(decoded)) {
            throw new IOException("Path traversal attempt detected: " + path);
        }
        
        // Normalize segments
        String[] segments = decoded.split("/");
        List<String> normalized = new ArrayList<>();
        
        for (String segment : segments) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            
            if (segment.equals("..")) {
                // Only allow going up if not at root
                if (!normalized.isEmpty()) {
                    normalized.remove(normalized.size() - 1);
                }
                // Silently ignore .. at root level
            } else {
                // Additional security: reject suspicious segments
                if (isValidSegment(segment)) {
                    normalized.add(segment);
                } else {
                    throw new IOException("Invalid path segment: " + segment);
                }
            }
        }
        
        if (normalized.isEmpty()) {
            return "/";
        }
        
        return "/" + String.join("/", normalized);
    }
    
    /**
     * FIX: Recursively decode to prevent double/multiple encoding bypass
     * Example: %252e%252e%252f → %2e%2e%2f → ../ (blocked)
     */
    private static String recursiveDecode(String value) throws IOException {
        String current = value;
        String previous;
        int iterations = 0;
        
        do {
            previous = current;
            try {
                current = URLDecoder.decode(current, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                // Invalid encoding - use current value
                break;
            }
            
            // Prevent infinite loops from malicious input
            if (++iterations > MAX_DECODE_ITERATIONS) {
                throw new IOException("Too many URL decode iterations - possible attack");
            }
            
        } while (!current.equals(previous));
        
        return current;
    }
    
    /**
     * Check if path contains traversal attempts
     */
    private static boolean containsPathTraversal(String path) {
        // Check for various path traversal patterns
        return path.matches(".*(^|/)\\.\\.(/|$).*") ||  // ../  or  /..
               path.contains("/../") ||
               path.startsWith("../") ||
               path.endsWith("/..") ||
               path.equals("..");
    }
    
    /**
     * Validate path segment for suspicious patterns
     */
    private static boolean isValidSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        
        // Reject segments with control characters
        for (char c : segment.toCharArray()) {
            if (Character.isISOControl(c)) {
                return false;
            }
        }
        
        // Additional checks can be added here
        return true;
    }
    
    /**
     * URL decode string with error handling
     */
    private static String urlDecode(String value) {
        if (value == null) {
            return "";
        }
        
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
    
    // ========== CUSTOM EXCEPTION ==========
    
    /**
     * Exception thrown when request is too large
     */
    public static class RequestTooLargeException extends IOException {
        public RequestTooLargeException(String message) {
            super(message);
        }
    }
}