package com.nova.framework.http;

import java.util.Map;

/**
 * Immutable HTTP request using Java record
 * 
 * @param method        HTTP method
 * @param path          Request path
 * @param headers       Request headers (case-insensitive)
 * @param body          Request body
 * @param queryParams   Query parameters
 * @param pathParams    Path parameters (from route matching)
 * @param clientAddress Client IP address
 */
public record HTTPRequest(
        HTTPMethod method,
        String path,
        Map<String, String> headers,
        String body,
        Map<String, String> queryParams,
        Map<String, String> pathParams,
        String clientAddress) {
    /**
     * Compact constructor with validation
     */
    public HTTPRequest {
        if (method == null) {
            throw new IllegalArgumentException("Method cannot be null");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }

        // Make collections immutable
        headers = headers != null ? Map.copyOf(headers) : Map.of();
        queryParams = queryParams != null ? Map.copyOf(queryParams) : Map.of();
        pathParams = pathParams != null ? Map.copyOf(pathParams) : Map.of();
    }

    /**
     * Get header value (case-insensitive)
     */
    public String getHeader(String name) {
        if (name == null)
            return null;

        // Case-insensitive lookup
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Get query parameter
     */
    public String getQueryParam(String name) {
        return queryParams.get(name);
    }

    /**
     * Get path parameter (from route like /api/:id)
     */
    public String getPathParam(String name) {
        return pathParams.get(name);
    }

    /**
     * Get cookie value
     */
    public String getCookie(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        String cookieHeader = getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        String[] cookies = cookieHeader.split(";");
        for (String cookie : cookies) {
            String trimmed = cookie.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            String[] parts = trimmed.split("=", 2);
            if (parts.length >= 1 && parts[0].equals(name)) {
                // Return decoded value if present, empty string otherwise
                if (parts.length == 2) {
                    try {
                        return java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        // Return raw value if decoding fails
                        return parts[1];
                    }
                }
                return "";
            }
        }
        return null;
    }

    public HTTPMethod method() {
        return method;
    }

    public String getPath() {
        return path;
    }
    
    public String getBody() {
        return body;
    }

    public String getClientIP() {
        return clientAddress;
    }
    
    /**
     * Check if this is a WebSocket upgrade request
     */
    public boolean isWebSocketUpgrade() {
        String upgrade = getHeader("Upgrade");
        String connection = getHeader("Connection");
        return "websocket".equalsIgnoreCase(upgrade) &&
                connection != null && connection.toLowerCase().contains("upgrade");
    }
}
