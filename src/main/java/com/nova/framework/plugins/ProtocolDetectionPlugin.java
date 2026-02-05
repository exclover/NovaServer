package com.nova.framework.plugins;

import com.nova.framework.plugin.*;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protocol Detection Plugin - OPTIONAL
 * Detects and routes custom protocols by magic bytes
 */
public final class ProtocolDetectionPlugin extends BasePlugin {

    private final Map<String, ProtocolHandler> handlers = new ConcurrentHashMap<>();
    private ProtocolHandler defaultHandler = null;

    @Override
    public String id() {
        return "protocol-detection";
    }

    @Override
    public boolean isDefault() {
        return false; // Optional plugin
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.VERY_HIGH; // Detect protocol early
    }

    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        context.log("Protocol Detection plugin initialized");
    }

    // ========== PUBLIC API ==========

    /**
     * Register handler for specific magic bytes
     */
    public ProtocolDetectionPlugin onCustomProtocol(byte[] magicBytes, ProtocolHandler handler) {
        if (magicBytes == null || magicBytes.length == 0) {
            throw new IllegalArgumentException("Magic bytes cannot be null or empty");
        }

        String key = bytesToHex(magicBytes);
        handlers.put(key, handler);
        context.log("Registered custom protocol: " + key);
        return this;
    }

    /**
     * Register DEFAULT handler for unknown protocols
     * This is the fallback when magic bytes don't match
     */
    public ProtocolDetectionPlugin onCustomProtocol(ProtocolHandler handler) {
        this.defaultHandler = handler;
        context.log("Registered default protocol handler");
        return this;
    }

    /**
     * Detect and handle protocol
     * 
     * @param socket The client socket
     * @param input The BufferedInputStream (positioned AFTER magic bytes have been read)
     * @param magicBytes The first 4 bytes that were read
     * @return true if protocol was handled, false if should close connection
     */
    public boolean handleProtocol(Socket socket, BufferedInputStream input, byte[] magicBytes) {
        String key = bytesToHex(magicBytes);

        // Try specific handler first
        ProtocolHandler handler = handlers.get(key);

        if (handler != null) {
            try {
                context.log("Detected protocol: " + key);
                handler.handle(socket, input, magicBytes);
                return true;
            } catch (Exception e) {
                context.error("Protocol handler error: " + key, e);
                return false;
            }
        }

        // Try default handler
        if (defaultHandler != null) {
            try {
                context.log("Unknown protocol, using default handler: " + key);
                defaultHandler.handle(socket, input, magicBytes);
                return true;
            } catch (Exception e) {
                context.error("Default protocol handler error", e);
                return false;
            }
        }

        // No handler found - close connection
        context.warn("Unknown protocol, no default handler, closing connection: " + key);
        return false;
    }

    /**
     * Get number of registered protocol handlers
     */
    public int getHandlerCount() {
        return handlers.size() + (defaultHandler != null ? 1 : 0);
    }

    /**
     * Check if a specific protocol is registered
     */
    public boolean hasProtocol(byte[] magicBytes) {
        String key = bytesToHex(magicBytes);
        return handlers.containsKey(key);
    }

    /**
     * Convert byte array to hex string
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return hex.toString();
    }

    /**
     * Protocol handler functional interface
     * 
     * @param socket The client socket
     * @param input The input stream (BufferedInputStream) - magic bytes already consumed
     * @param magicBytes The magic bytes that were detected
     */
    @FunctionalInterface
    public interface ProtocolHandler {
        void handle(Socket socket, InputStream input, byte[] magicBytes) throws Exception;
    }
}
