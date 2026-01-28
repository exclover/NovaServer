package com.nova.framework.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;

public class Response {
    
    private final OutputStream out;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private final List<String> cookies = new ArrayList<>();
    private int status = 200;
    private volatile boolean sent = false;
    private boolean compressionEnabled = false;
    
    public Response(OutputStream out) {
        if (out == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        this.out = out;
    }
    
    // ========== STATUS ==========
    
    public Response status(int code) {
        checkNotSent();
        if (code < 100 || code > 599) {
            throw new IllegalArgumentException("Invalid status code: " + code);
        }
        this.status = code;
        return this;
    }
    
    public int getStatus() { return status; }
    
    // ========== HEADERS ==========
    
    public Response setHeader(String key, String value) {
        checkNotSent();
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be null");
        }
        if (value != null) {
            headers.put(key, value);
        }
        return this;
    }
    
    public Response setHeaders(Map<String, String> headers) {
        checkNotSent();
        if (headers != null) {
            headers.forEach(this::setHeader);
        }
        return this;
    }
    
    public String getHeader(String key) {
        return headers.get(key);
    }
    
    // ========== COOKIES ==========
    
    public Response cookie(String name, String value) {
        return cookie(name, value, null);
    }
    
    public Response cookie(String name, String value, CookieOptions options) {
        checkNotSent();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Cookie name cannot be null");
        }
        
        if (options == null) {
            options = new CookieOptions();
        }
        
        StringBuilder cookie = new StringBuilder();
        cookie.append(name).append("=").append(value != null ? value : "");
        
        if (options.maxAge != null) {
            cookie.append("; Max-Age=").append(options.maxAge);
        }
        if (options.path != null) {
            cookie.append("; Path=").append(options.path);
        }
        if (options.domain != null) {
            cookie.append("; Domain=").append(options.domain);
        }
        if (options.secure) {
            cookie.append("; Secure");
        }
        if (options.httpOnly) {
            cookie.append("; HttpOnly");
        }
        if (options.sameSite != null) {
            cookie.append("; SameSite=").append(options.sameSite);
        }
        
        cookies.add(cookie.toString());
        return this;
    }
    
    // ========== SENDING ==========
    
    public void send(String content) throws IOException {
        if (content == null) content = "";
        send(content.getBytes(StandardCharsets.UTF_8));
    }
    
    public void send(byte[] body) throws IOException {
        checkNotSent();
        if (body == null) body = new byte[0];
        
        headers.putIfAbsent("Content-Type", "text/plain; charset=utf-8");
        
        if (compressionEnabled && body.length > 1024) {
            body = compress(body);
            headers.put("Content-Encoding", "gzip");
        }
        
        headers.put("Content-Length", String.valueOf(body.length));
        writeResponse(body);
    }
    
    public void json(String data) throws IOException {
        setHeader("Content-Type", "application/json; charset=utf-8");
        send(data);
    }
    
    public void json(Map<String, Object> data) throws IOException {
        json(toJson(data));
    }
    
    public void html(String html) throws IOException {
        setHeader("Content-Type", "text/html; charset=utf-8");
        send(html);
    }
    
    public void text(String text) throws IOException {
        setHeader("Content-Type", "text/plain; charset=utf-8");
        send(text);
    }
    
    public void xml(String xml) throws IOException {
        setHeader("Content-Type", "application/xml; charset=utf-8");
        send(xml);
    }
    
    public void file(File file) throws IOException {
        if (!file.exists()) {
            status(404).json("{\"error\": \"File not found\"}");
            return;
        }
        
        String contentType = guessContentType(file.getName());
        setHeader("Content-Type", contentType);
        
        byte[] fileContent = java.nio.file.Files.readAllBytes(file.toPath());
        send(fileContent);
    }
    
    public void redirect(String location) throws IOException {
        redirect(location, 302);
    }
    
    public void redirect(String location, int statusCode) throws IOException {
        if (location == null || location.trim().isEmpty()) {
            throw new IllegalArgumentException("Redirect location cannot be null");
        }
        status(statusCode);
        setHeader("Location", location);
        send("");
    }
    
    public void noContent() throws IOException {
        status(204);
        send("");
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    public void ok(String message) throws IOException {
        status(200).json(String.format("{\"message\": \"%s\"}", escapeJson(message)));
    }
    
    public void created(String message) throws IOException {
        status(201).json(String.format("{\"message\": \"%s\"}", escapeJson(message)));
    }
    
    public void badRequest(String message) throws IOException {
        status(400).json(String.format("{\"error\": \"%s\"}", escapeJson(message)));
    }
    
    public void unauthorized(String message) throws IOException {
        status(401).json(String.format("{\"error\": \"%s\"}", escapeJson(message)));
    }
    
    public void forbidden(String message) throws IOException {
        status(403).json(String.format("{\"error\": \"%s\"}", escapeJson(message)));
    }
    
    public void notFound(String message) throws IOException {
        status(404).json(String.format("{\"error\": \"%s\"}", escapeJson(message)));
    }
    
    public void serverError(String message) throws IOException {
        status(500).json(String.format("{\"error\": \"%s\"}", escapeJson(message)));
    }
    
    // ========== COMPRESSION ==========
    
    public Response enableCompression() {
        this.compressionEnabled = true;
        return this;
    }
    
    private byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            gzip.write(data);
        }
        return baos.toByteArray();
    }
    
    // ========== INTERNAL ==========
    
    private void writeResponse(byte[] body) throws IOException {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ")
                .append(status)
                .append(" ")
                .append(getStatusText())
                .append("\r\n");
        
        headers.forEach((key, value) -> 
            response.append(key).append(": ").append(value).append("\r\n")
        );
        
        for (String cookie : cookies) {
            response.append("Set-Cookie: ").append(cookie).append("\r\n");
        }
        
        response.append("\r\n");
        
        out.write(response.toString().getBytes(StandardCharsets.UTF_8));
        out.write(body);
        out.flush();
        
        sent = true;
    }
    
    private String getStatusText() {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 301 -> "Moved Permanently";
            case 302 -> "Found";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Unknown";
        };
    }
    
    private void checkNotSent() {
        if (sent) {
            throw new IllegalStateException("Response already sent");
        }
    }
    
    public boolean isSent() {
        return sent;
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    private String toJson(Map<String, Object> data) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) json.append(",");
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(escapeJson((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value).append("\"");
            }
            
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }
    
    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }
    
    // ========== COOKIE OPTIONS ==========
    
    public static class CookieOptions {
        Integer maxAge;
        String path = "/";
        String domain;
        boolean secure;
        boolean httpOnly = true;
        String sameSite;
        
        public CookieOptions maxAge(int seconds) {
            this.maxAge = seconds;
            return this;
        }
        
        public CookieOptions path(String path) {
            this.path = path;
            return this;
        }
        
        public CookieOptions domain(String domain) {
            this.domain = domain;
            return this;
        }
        
        public CookieOptions secure(boolean secure) {
            this.secure = secure;
            return this;
        }
        
        public CookieOptions httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }
        
        public CookieOptions sameSite(String sameSite) {
            this.sameSite = sameSite;
            return this;
        }
    }
}