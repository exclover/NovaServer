package com.nova.framework.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP response builder
 */
public final class HTTPResponse {

    private final OutputStream outputStream;
    private final Map<String, String> headers = new HashMap<>();
    private HTTPStatus status = HTTPStatus.OK;
    private boolean sent = false;

    public HTTPResponse(OutputStream outputStream) {
        if (outputStream == null) {
            throw new IllegalArgumentException("OutputStream cannot be null");
        }
        this.outputStream = outputStream;
        // Default headers
        setHeader("Content-Type", "text/plain; charset=utf-8");
        setHeader("Server", "NovaServer/3.0");
        setHeader("Connection", "close");
    }

    /**
     * Set status code
     */
    public HTTPResponse status(int code) {
        HTTPStatus httpStatus = HTTPStatus.valueOf(code);
        this.status = httpStatus != null ? httpStatus : HTTPStatus.OK;
        return this;
    }

    /**
     * Set status
     */
    public HTTPResponse status(HTTPStatus status) {
        this.status = status;
        return this;
    }

    /**
     * Set header with validation to prevent header injection
     */
    public HTTPResponse setHeader(String name, String value) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Header name cannot be null or empty");
        }
        if (value == null) {
            value = "";
        }
        // Prevent header injection attacks
        if (name.contains("\r") || name.contains("\n") || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("Header name or value contains invalid characters (CR/LF)");
        }
        headers.put(name, value);
        return this;
    }

    /**
     * Set content type
     */
    public HTTPResponse contentType(String contentType) {
        return setHeader("Content-Type", contentType);
    }

    /**
     * Set cookie
     */
    public HTTPResponse cookie(Cookie cookie) {
        setHeader("Set-Cookie", cookie.toSetCookieHeader());
        return this;
    }

    /**
     * Set cookie (simple)
     */
    public HTTPResponse cookie(String name, String value) {
        return cookie(new Cookie(name, value));
    }

    /**
     * Send file for download
     */
    public void sendFile(java.io.File file) throws IOException {
        sendFile(file, file.getName());
    }

    /**
     * Send file with custom filename
     */
    public void sendFile(java.io.File file, String filename) throws IOException {
        if (!file.exists() || !file.isFile()) {
            status(HTTPStatus.NOT_FOUND);
            send("File not found");
            return;
        }

        // Set headers
        setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        setHeader("Content-Type", "application/octet-stream");
        setHeader("Content-Length", String.valueOf(file.length()));

        // Write headers
        writeHeaders();

        // Stream file
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        outputStream.flush();
        sent = true;
    }

    /**
     * Send HTML response
     */
    public void html(String html) throws IOException {
        setHeader("Content-Type", "text/html; charset=utf-8");
        send(html);
    }

    /**
     * Send JSON response
     */
    public void json(String json) throws IOException {
        setHeader("Content-Type", "application/json; charset=utf-8");
        send(json);
    }



    /**
     * Send response with custom content type
     */
    public void send(String content) throws IOException {
        if (sent) {
            throw new IllegalStateException("Response already sent");
        }

        byte[] bodyBytes = content != null ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
        send(bodyBytes);
    }
    
    /**
     * Send response with custom content type
     */
    public void send(byte[] bodyBytes) throws IOException {
        if (sent) {
            throw new IllegalStateException("Response already sent");
        }

        setHeader("Content-Length", String.valueOf(bodyBytes.length));

        writeHeaders();

        // Write body
        if (bodyBytes.length > 0) {
            outputStream.write(bodyBytes);
        }

        outputStream.flush();
        sent = true;
    }

    private void writeHeaders() throws IOException {
        // Write status line
        String statusLine = String.format("HTTP/1.1 %d %s\r\n",
                status.getCode(), status.getMessage());
        outputStream.write(statusLine.getBytes(StandardCharsets.UTF_8));

        // Write headers
        for (Map.Entry<String, String> header : headers.entrySet()) {
            String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
            outputStream.write(headerLine.getBytes(StandardCharsets.UTF_8));
        }

        // Write blank line
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Check if response was sent
     */
    public boolean isSent() {
        return sent;
    }
}
