package com.nova.framework.websocket;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WSConnection {
    
    final Socket socket; // Package-private for WSUpgrader access
    private final String id;
    private final String clientIP;
    private final Map<String, String> routeParams;
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private volatile boolean open = true;
    
    public WSConnection(Socket socket, String clientIP, Map<String, String> routeParams) {
        this.socket = socket;
        this.id = UUID.randomUUID().toString();
        this.clientIP = clientIP;
        this.routeParams = routeParams != null ? new HashMap<>(routeParams) : new HashMap<>();
    }
    
    public String id() { return id; }
    public String clientIP() { return clientIP; }
    public boolean isOpen() { return open && !socket.isClosed(); }
    
    public String param(String name) {
        return routeParams.get(name);
    }
    
    public void set(String key, Object value) {
        attributes.put(key, value);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        return type.isInstance(value) ? (T) value : null;
    }
    
    public void sendText(String message) throws IOException {
        if (!isOpen()) throw new IOException("Connection closed");
        WSFrame.text(message).write(socket.getOutputStream());
    }
    
    public void sendBinary(byte[] data) throws IOException {
        if (!isOpen()) throw new IOException("Connection closed");
        WSFrame.binary(data).write(socket.getOutputStream());
    }
    
    public void sendPing() throws IOException {
        if (!isOpen()) throw new IOException("Connection closed");
        WSFrame.ping().write(socket.getOutputStream());
    }
    
    public void sendPong() throws IOException {
        if (!isOpen()) throw new IOException("Connection closed");
        WSFrame.pong().write(socket.getOutputStream());
    }
    
    public void close() throws IOException {
        close(1000, "Normal closure");
    }
    
    public void close(int statusCode, String reason) throws IOException {
        if (open) {
            open = false;
            try {
                WSFrame.close(statusCode, reason).write(socket.getOutputStream());
            } catch (IOException ignored) {
                // Ignore errors during close
            } finally {
                socket.close();
            }
        }
    }
}