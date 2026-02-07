package com.nova.framework.plugins;

import com.nova.framework.http.HTTPRequest;
import com.nova.framework.plugin.*;
import com.nova.framework.plugins.websocket.WebSocketConnection;

import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * WebSocket Plugin - OPTIONAL
 * Provides WebSocket protocol support
 */
public final class WebSocketPlugin extends BasePlugin {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final Map<String, WSHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicLong connectionId = new AtomicLong(0);

    @Override
    public String id() {
        return "websocket";
    }

    @Override
    public boolean isDefault() {
        return false; // Optional
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.HIGH; // Handle before routing
    }

    @Override
    public Set<String> dependencies() {
        return Set.of(); // No dependencies (routing removed since we bypass it)
    }

    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        context.log("WebSocket plugin initialized");
    }

    // ========== PUBLIC API ==========

    /**
     * Register WebSocket endpoint
     */
    public WebSocketPlugin websocket(String path, Consumer<WebSocketConnection> configure) {
        handlers.put(path, new WSHandler(configure));
        context.log("Registered WebSocket endpoint: " + path);
        return this;
    }

    /**
     * Handle WebSocket upgrade
     * 
     * @param socket The client socket
     * @param request The HTTP upgrade request
     * @return true if upgrade successful, false otherwise
     */
    public boolean handleUpgrade(Socket socket, HTTPRequest request) {
        return handleUpgrade(socket, request, null);
    }

    /**
     * Handle WebSocket upgrade with cleanup callback
     * 
     * @param socket The client socket
     * @param request The HTTP upgrade request
     * @param onConnectionClose Callback to run when connection closes (for cleanup)
     * @return true if upgrade successful, false otherwise
     */
    @SuppressWarnings("unused")
    public boolean handleUpgrade(Socket socket, HTTPRequest request, Runnable onConnectionClose) {
        WSHandler handler = handlers.get(request.path());

        if (handler == null) {
            return false; // Not a WebSocket endpoint
        }

        try {
            // Validate WebSocket upgrade headers
            String key = request.getHeader("Sec-WebSocket-Key");
            if (key == null || key.isEmpty()) {
                context.warn("WebSocket upgrade missing Sec-WebSocket-Key header");
                return false;
            }

            String upgrade = request.getHeader("Upgrade");
            String connection = request.getHeader("Connection");
            
            if (!"websocket".equalsIgnoreCase(upgrade)) {
                context.warn("WebSocket upgrade missing correct Upgrade header");
                return false;
            }

            // Generate accept key
            String accept = generateAcceptKey(key);

            // Send handshake response
            OutputStream output = socket.getOutputStream();
            String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
            output.write(response.getBytes(StandardCharsets.UTF_8));
            output.flush();

            socket.setKeepAlive(true);
            socket.setSoTimeout(0);

            // Create WebSocket connection
            String id = "ws-" + connectionId.incrementAndGet();
            WebSocketConnection wsConnection = new WebSocketConnection(socket, id);

            // Set cleanup handler if provided
            if (onConnectionClose != null) {
                wsConnection.onCleanup(onConnectionClose);
            }

            // Configure connection with user handler
            handler.configure.accept(wsConnection);

            // Start reading messages in executor (non-blocking for caller)
            context.executor().submit(() -> {
                try {
                    wsConnection.startReading();
                } catch (Exception e) {
                    context.error("WebSocket connection error for " + id, e);
                }
            });

            context.log("WebSocket connection established: " + id + " on " + request.path());
            return true;

        } catch (Exception e) {
            context.error("WebSocket upgrade failed", e);
            return false;
        }
    }

    private String generateAcceptKey(String key) throws Exception {
        String combined = key + MAGIC;
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Get number of registered WebSocket endpoints
     */
    public int getEndpointCount() {
        return handlers.size();
    }

    /**
     * Check if path is a WebSocket endpoint
     */
    public boolean hasEndpoint(String path) {
        return handlers.containsKey(path);
    }

    private record WSHandler(Consumer<WebSocketConnection> configure) { }
}