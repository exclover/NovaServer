package com.nova.framework.http;

/**
 * HTTP methods
 */
public enum HTTPMethod {
    GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD, TRACE, CONNECT;

    public static HTTPMethod parse(String method) {
        if (method == null)
            return null;
        try {
            return valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
