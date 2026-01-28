package com.nova.framework.websocket;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class WebSocketHandler {
    
    private final String path;
    private final Set<WSConnection> connections = new CopyOnWriteArraySet<>();
    
    private volatile Consumer<WSConnection> onConnect;
    private volatile Consumer<WSConnection> onClose;
    private volatile BiConsumer<WSConnection, String> onMessage;
    private volatile BiConsumer<WSConnection, byte[]> onBinary;
    private volatile BiConsumer<WSConnection, Exception> onError;
    
    public WebSocketHandler(String path) {
        this.path = path;
    }
    
    public String path() { return path; }
    public int connectionCount() { return connections.size(); }
    
    public WebSocketHandler onConnect(Consumer<WSConnection> handler) {
        this.onConnect = handler;
        return this;
    }
    
    public WebSocketHandler onMessage(BiConsumer<WSConnection, String> handler) {
        this.onMessage = handler;
        return this;
    }
    
    public WebSocketHandler onBinary(BiConsumer<WSConnection, byte[]> handler) {
        this.onBinary = handler;
        return this;
    }
    
    public WebSocketHandler onClose(Consumer<WSConnection> handler) {
        this.onClose = handler;
        return this;
    }
    
    public WebSocketHandler onError(BiConsumer<WSConnection, Exception> handler) {
        this.onError = handler;
        return this;
    }
    
    public void addConnection(WSConnection conn) {
        connections.add(conn);
    }
    
    public void removeConnection(WSConnection conn) {
        connections.remove(conn);
    }
    
    public void broadcast(String message) {
        connections.forEach(conn -> {
            try {
                if (conn.isOpen()) {
                    conn.sendText(message);
                }
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(conn, e);
                }
            }
        });
    }
    
    public void broadcastExcept(String message, WSConnection except) {
        connections.stream()
            .filter(conn -> conn != except && conn.isOpen())
            .forEach(conn -> {
                try {
                    conn.sendText(message);
                } catch (Exception e) {
                    if (onError != null) {
                        onError.accept(conn, e);
                    }
                }
            });
    }
    
    Consumer<WSConnection> getOnConnect() { return onConnect; }
    Consumer<WSConnection> getOnClose() { return onClose; }
    BiConsumer<WSConnection, String> getOnMessage() { return onMessage; }
    BiConsumer<WSConnection, byte[]> getOnBinary() { return onBinary; }
    BiConsumer<WSConnection, Exception> getOnError() { return onError; }
}