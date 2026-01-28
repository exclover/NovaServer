package com.nova.framework.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced HTTP Request with advanced features
 * Thread-safe and immutable after parsing
 */
public class Request {
    
    private String method;
    private String path;
    private String httpVersion;
    private String body;
    private String clientIP;
    private String rawQuery;
    
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final Map<String, List<String>> queryParams = new LinkedHashMap<>();
    private final Map<String, String> cookies = new LinkedHashMap<>();
    private final Map<String, String> routeParams = new LinkedHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    // Lazy-parsed JSON body
    private transient Map<String, Object> jsonBody;
    private transient Map<String, String> formData;
    
    Request() {}
    
    // ========== BASIC GETTERS ==========
    
    public String method() { return method; }
    public String path() { return path; }
    public String body() { return body; }
    public String clientIP() { return clientIP; }
    public String httpVersion() { return httpVersion; }
    public String rawQuery() { return rawQuery; }
    
    // Legacy compatibility
    public String getMethod() { return method; }
    public String getPath() { return path; }
    public String getBody() { return body; }
    public String getClientIP() { return clientIP; }
    public String getHttpVersion() { return httpVersion; }
    
    // ========== HEADERS ==========
    
    public Map<String, String> headers() {
        return Collections.unmodifiableMap(headers);
    }
    
    public String header(String name) {
        if (name == null) return null;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
    
    public String header(String name, String defaultValue) {
        String value = header(name);
        return value != null ? value : defaultValue;
    }
    
    public boolean hasHeader(String name) {
        return header(name) != null;
    }
    
    public Optional<String> headerOptional(String name) {
        return Optional.ofNullable(header(name));
    }
    
    // Legacy compatibility
    public String getHeader(String name) { return header(name); }
    public Map<String, String> getHeaders() { return headers(); }
    
    // ========== QUERY PARAMETERS ==========
    
    public Map<String, List<String>> queryParams() {
        return Collections.unmodifiableMap(queryParams);
    }
    
    public String query(String name) {
        List<String> values = queryParams.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }
    
    public String query(String name, String defaultValue) {
        String value = query(name);
        return value != null ? value : defaultValue;
    }
    
    public List<String> queryAll(String name) {
        return queryParams.getOrDefault(name, Collections.emptyList());
    }
    
    public int queryInt(String name, int defaultValue) {
        String value = query(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public long queryLong(String name, long defaultValue) {
        String value = query(name);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public boolean queryBool(String name, boolean defaultValue) {
        String value = query(name);
        if (value == null) return defaultValue;
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }
    
    // Legacy compatibility
    public String getQueryParam(String name) { return query(name); }
    public Map<String, String> getQuery() {
        Map<String, String> simple = new HashMap<>();
        queryParams.forEach((k, v) -> simple.put(k, v.isEmpty() ? null : v.get(0)));
        return Collections.unmodifiableMap(simple);
    }
    
    // ========== COOKIES ==========
    
    public Map<String, String> cookies() {
        return Collections.unmodifiableMap(cookies);
    }
    
    public String cookie(String name) {
        return cookies.get(name);
    }
    
    public String cookie(String name, String defaultValue) {
        return cookies.getOrDefault(name, defaultValue);
    }
    
    public boolean hasCookie(String name) {
        return cookies.containsKey(name);
    }
    
    // Legacy compatibility
    public String getCookie(String name) { return cookie(name); }
    public Map<String, String> getCookies() { return cookies(); }
    
    // ========== ROUTE PARAMETERS ==========
    
    public Map<String, String> routeParams() {
        return Collections.unmodifiableMap(routeParams);
    }
    
    public String param(String name) {
        return routeParams.get(name);
    }
    
    public String param(String name, String defaultValue) {
        return routeParams.getOrDefault(name, defaultValue);
    }
    
    public int paramInt(String name, int defaultValue) {
        String value = param(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    public long paramLong(String name, long defaultValue) {
        String value = param(name);
        if (value == null) return defaultValue;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    // Legacy compatibility
    public String getRouteParam(String name) { return param(name); }
    public Map<String, String> getRouteParams() { return routeParams(); }
    public void setParams(Map<String, String> params) {
        routeParams.clear();
        if (params != null) {
            routeParams.putAll(params);
        }
    }
    
    // ========== BODY PARSING ==========
    
    public boolean hasBody() {
        return body != null && !body.isEmpty();
    }
    
    public String contentType() {
        return header("Content-Type");
    }
    
    public int contentLength() {
        String cl = header("Content-Length");
        if (cl != null) {
            try {
                return Integer.parseInt(cl);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
    
    public boolean isJson() {
        String ct = contentType();
        return ct != null && ct.toLowerCase().contains("application/json");
    }
    
    public boolean isFormData() {
        String ct = contentType();
        return ct != null && ct.toLowerCase().contains("application/x-www-form-urlencoded");
    }
    
    public boolean isMultipart() {
        String ct = contentType();
        return ct != null && ct.toLowerCase().contains("multipart/form-data");
    }
    
    // Simple JSON parsing (basic implementation)
    public Map<String, Object> json() {
        if (jsonBody == null && isJson() && hasBody()) {
            jsonBody = parseSimpleJson(body);
        }
        return jsonBody != null ? Collections.unmodifiableMap(jsonBody) : Collections.emptyMap();
    }
    
    public String jsonString(String key) {
        Object value = json().get(key);
        return value != null ? value.toString() : null;
    }
    
    public Integer jsonInt(String key) {
        Object value = json().get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    public Map<String, String> form() {
        if (formData == null && isFormData() && hasBody()) {
            formData = parseFormData(body);
        }
        return formData != null ? Collections.unmodifiableMap(formData) : Collections.emptyMap();
    }
    
    public String formValue(String key) {
        return form().get(key);
    }
    
    // ========== WEBSOCKET ==========
    
    public boolean isWebSocketUpgrade() {
        String upgrade = header("Upgrade");
        String connection = header("Connection");
        return "websocket".equalsIgnoreCase(upgrade) &&
               connection != null && connection.toLowerCase().contains("upgrade");
    }
    
    // ========== AUTHENTICATION ==========
    
    public String authorization() {
        return header("Authorization");
    }
    
    public String bearerToken() {
        String auth = authorization();
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }
    
    public String basicAuth() {
        String auth = authorization();
        if (auth != null && auth.startsWith("Basic ")) {
            return auth.substring(6);
        }
        return null;
    }
    
    // Legacy compatibility
    public String getAuthorization() { return authorization(); }
    public String getBearerToken() { return bearerToken(); }
    
    // ========== ATTRIBUTES ==========
    
    public void set(String key, Object value) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
    
    public Object get(String key) {
        return attributes.get(key);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    public boolean has(String key) {
        return attributes.containsKey(key);
    }
    
    // Legacy compatibility
    public void setAttribute(String key, Object value) { set(key, value); }
    public Object getAttribute(String key) { return get(key); }
    public <T> T getAttribute(String key, Class<T> type) { return get(key, type); }
    public boolean hasAttribute(String key) { return has(key); }
    
    // ========== OTHER ==========
    
    public String userAgent() {
        return header("User-Agent");
    }
    
    public String referer() {
        return header("Referer");
    }
    
    public String host() {
        return header("Host");
    }
    
    public boolean isSecure() {
        return httpVersion != null && httpVersion.contains("HTTPS");
    }
    
    public boolean accepts(String type) {
        String accept = header("Accept");
        return accept != null && accept.contains(type);
    }
    
    public boolean acceptsJson() {
        return accepts("application/json");
    }
    
    public boolean acceptsHtml() {
        return accepts("text/html");
    }
    
    // Legacy compatibility
    public String getUserAgent() { return userAgent(); }
    public String getContentType() { return contentType(); }
    public int getContentLength() { return contentLength(); }
    
    // ========== PACKAGE-PRIVATE SETTERS ==========
    
    void setMethod(String method) { this.method = method; }
    void setPath(String path) { this.path = path; }
    void setBody(String body) { this.body = body; }
    void setClientIP(String clientIP) { this.clientIP = clientIP; }
    void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }
    void setRawQuery(String query) { this.rawQuery = query; }
    
    void addHeader(String key, String value) {
        if (key != null && value != null) {
            headers.put(key, value);
        }
    }
    
    void addQueryParam(String key, String value) {
        if (key != null && value != null) {
            queryParams.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
    }
    
    void addCookie(String key, String value) {
        if (key != null && value != null) {
            cookies.put(key, value);
        }
    }
    
    void addRouteParam(String key, String value) {
        if (key != null && value != null) {
            routeParams.put(key, value);
        }
    }
    
    // ========== PARSING HELPERS ==========
    
    private Map<String, Object> parseSimpleJson(String json) {
        // Simple JSON parser (for basic use cases)
        // For production, use Jackson or Gson
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return result;
        }
        
        json = json.trim();
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return result;
        }
        
        json = json.substring(1, json.length() - 1);
        String[] pairs = json.split(",");
        
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length == 2) {
                String key = kv[0].trim().replaceAll("\"", "");
                String value = kv[1].trim().replaceAll("\"", "");
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    private Map<String, String> parseFormData(String data) {
        Map<String, String> result = new HashMap<>();
        if (data == null || data.trim().isEmpty()) {
            return result;
        }
        
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2) {
                result.put(urlDecode(kv[0]), urlDecode(kv[1]));
            }
        }
        
        return result;
    }
    
    private String urlDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "Request{method='%s', path='%s', clientIP='%s', contentType='%s', bodySize=%d}",
            method, path, clientIP, contentType(), body != null ? body.length() : 0
        );
    }
}