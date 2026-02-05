package com.nova.framework;

import com.nova.framework.config.NovaConfig;
import com.nova.framework.core.ConnectionPool;
import com.nova.framework.http.*;
import com.nova.framework.plugin.*;
import com.nova.framework.plugins.MiddlewarePlugin;
import com.nova.framework.plugins.ProtocolDetectionPlugin;
import com.nova.framework.plugins.RoutingPlugin;
import com.nova.framework.plugins.WebSocketPlugin;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Modern NovaServer v3.0 - Plugin-Based Architecture
 * 
 * Minimal core server that delegates all functionality to plugins
 */
public final class NovaServer {

    private final NovaConfig config;
    private final PluginManager pluginManager;
    private final ConnectionPool connectionPool;
    private final ExecutorService executor;
    private PluginContext pluginContext;

    private volatile ServerSocket serverSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // ========== CONSTRUCTOR ==========

    public NovaServer(int port) {
        this(NovaConfig.builder().port(port).build());
    }

    public NovaServer(NovaConfig config) {
        this.config = config;
        this.pluginManager = new PluginManager();
        this.connectionPool = new ConnectionPool(config.maxConnections());
        this.executor = createExecutor();

        // Register and initialize default plugins
        registerDefaultPlugins();
    }

    // ========== PLUGIN API ==========

    /**
     * Register a plugin
     */
    public <T extends Plugin> NovaServer use(T plugin) {
        if (running.get()) {
            throw new IllegalStateException("Cannot register plugins while server running");
        }
        try {
            pluginManager.register(plugin);
            
            // If context already exists, initialize this plugin immediately
            if (pluginContext != null) {
                pluginManager.initialize(plugin, pluginContext);
            }
        } catch (PluginException e) {
            throw new RuntimeException("Failed to register plugin: " + plugin.id(), e);
        }
        return this;
    }

    /**
     * Get plugin by ID and type
     */
    public <T extends Plugin> T getPlugin(String id, Class<T> type) {
        return pluginManager.getPlugin(id, type);
    }

    // ========== CONVENIENCE METHODS FOR DEFAULT PLUGINS ==========

    /**
     * Get routing plugin (shortcut)
     */
    public RoutingPlugin routing() {
        return getPlugin("routing", RoutingPlugin.class);
    }

    /**
     * Get middleware plugin (shortcut)
     */
    public MiddlewarePlugin middleware() {
        return getPlugin("middleware", MiddlewarePlugin.class);
    }

    /**
     * Check if plugin exists
     */
    public boolean hasPlugin(String pluginId) {
        return pluginManager.hasPlugin(pluginId);
    }

    // ========== LIFECYCLE ==========

    /**
     * Start the server
     */
    public NovaServer start() throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server already running");
        }

        try {
            // Create server socket
            serverSocket = new ServerSocket(config.port());
            serverSocket.setReuseAddress(true);

            running.set(true);

            log("NovaServer v3.0 starting on port " + config.port());
            log("Plugins: " + pluginManager.pluginCount());

            // Start plugins
            pluginManager.startAll();

            // Start accept loop
            executor.submit(this::acceptLoop);

            log("Server started successfully");

        } catch (Exception e) {
            running.set(false);
            cleanup();
            throw new IOException("Failed to start server", e);
        }

        return this;
    }

    /**
     * Stop the server
     */
    public void stop() {
        if (!running.get())
            return;

        log("Stopping server...");
        running.set(false);

        // Stop plugins
        pluginManager.stopAll();

        cleanup();

        log("Server stopped");
    }

    /**
     * Wait for server to terminate
     */
    public void await() {
        while (running.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    // ========== INTERNAL ==========

    private void registerDefaultPlugins() {
        try {
            // Create plugin context
            pluginContext = new PluginContext(
                    config,
                    executor,
                    this::handleLog,
                    pluginManager);
            
            // Register default plugins
            pluginManager.register(new RoutingPlugin());
            pluginManager.register(new MiddlewarePlugin());
            
            // Initialize all plugins
            pluginManager.initializeAll(pluginContext);
            
        } catch (PluginException e) {
            throw new RuntimeException("Failed to register default plugins", e);
        }
    }

    private void acceptLoop() {
        log("Accept loop started");

        while (running.get()) {
            Socket client = null;
            try {
                client = serverSocket.accept();

                if (!running.get()) {
                    closeSocket(client);
                    break;
                }

                if (!connectionPool.tryAcquire()) {
                    sendError(client, HTTPStatus.SERVICE_UNAVAILABLE, "Too many connections");
                    closeSocket(client);
                    continue;
                }

                final Socket finalClient = client;
                executor.submit(() -> handleClient(finalClient));

            } catch (IOException e) {
                if (running.get()) {
                    log("Accept error: " + e.getMessage());
                }
            }
        }

        log("Accept loop stopped");
    }

    private void handleClient(Socket client) {
        boolean keepAlive = false;
        
        try {
            configureSocket(client);

            // Wrap input stream in BufferedInputStream to support mark/reset
            InputStream rawInput = client.getInputStream();
            BufferedInputStream input = new BufferedInputStream(rawInput);
            
            // Peek at first few bytes to detect protocol
            input.mark(8); // Mark position to reset later
            
            byte[] magicBytes = new byte[4];
            int bytesRead = 0;
            while (bytesRead < 4) {
                int read = input.read(magicBytes, bytesRead, 4 - bytesRead);
                if (read == -1) {
                    closeSocket(client);
                    connectionPool.release();
                    return; // Connection closed
                }
                bytesRead += read;
            }
            
            input.reset(); // Reset to beginning for HTTP parser or protocol handler

            // Check if this is a custom protocol (non-HTTP)
            ProtocolDetectionPlugin protocolPlugin = getPlugin("protocol-detection", ProtocolDetectionPlugin.class);
            if (protocolPlugin != null && !isHttpRequest(magicBytes)) {
                // Reset stream to re-read magic bytes for protocol handler
                input.reset();
                
                boolean handled = protocolPlugin.handleProtocol(client, input, magicBytes);
                if (handled) {
                    // Custom protocol handled the connection
                    // Socket will be closed by protocol handler
                    keepAlive = true; // Prevent double-close
                    connectionPool.release(); // Protocol handler manages its own lifecycle
                    return;
                }
                // If not handled, reset again for HTTP processing
                input.reset();
            }

            // Parse HTTP request (use the BufferedInputStream)
            HTTPRequest request = HTTPParser.parse(
                    input,
                    client.getInetAddress().getHostAddress(),
                    config.maxRequestSize());

            // Check for WebSocket upgrade FIRST
            if (request.isWebSocketUpgrade()) {
                WebSocketPlugin wsPlugin = getPlugin("websocket", WebSocketPlugin.class);
                if (wsPlugin != null) {
                    // Pass cleanup callback to release connection pool when WS closes
                    boolean upgraded = wsPlugin.handleUpgrade(client, request, () -> {
                        connectionPool.release();
                        log("WebSocket connection closed, pool released");
                    });
                    
                    if (upgraded) {
                        // WebSocket upgraded successfully
                        // Connection is now managed by WebSocketConnection
                        // DO NOT close the socket - it's kept alive
                        // DO NOT release pool here - it will be released when WS closes
                        keepAlive = true;
                        return;
                    }
                }
            }

            // Normal HTTP request processing
            HTTPResponse response = new HTTPResponse(client.getOutputStream());

            // Process through plugins (by priority)
            processRequest(request, response);

            if (!response.isSent()) {
                response.status(HTTPStatus.NOT_FOUND)
                        .json("{\"error\": \"Not found\", \"path\": \"" + request.path() + "\"}");
            }

        } catch (HTTPParser.RequestTooLargeException e) {
            sendError(client, HTTPStatus.PAYLOAD_TOO_LARGE, "Request too large");
        } catch (IOException e) {
            log("Request handling error: " + e.getMessage());
        } finally {
            // Only release connection and close socket if NOT WebSocket
            if (!keepAlive) {
                connectionPool.release();
                closeSocket(client);
            }
        }
    }

    private void processRequest(HTTPRequest request, HTTPResponse response) {
        try {
            // Execute middleware
            MiddlewarePlugin middleware = getPlugin("middleware", MiddlewarePlugin.class);
            if (middleware != null) {
                boolean continueProcessing = middleware.execute(request, response);
                if (!continueProcessing || response.isSent()) {
                    return; // Middleware stopped processing
                }
            }

            // Execute routing
            RoutingPlugin routing = getPlugin("routing", RoutingPlugin.class);
            if (routing != null) {
                boolean handled = routing.getRouter().handle(request, response);
                if (handled) {
                    return; // Route handled the request
                }
            }

            // No handler found - will send 404 in handleClient
        } catch (Exception e) {
            log("Error processing request: " + e.getMessage());
            if (!response.isSent()) {
                try {
                    response.status(HTTPStatus.INTERNAL_SERVER_ERROR)
                            .json("{\"error\": \"Internal server error\"}");
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void configureSocket(Socket socket) throws IOException {
        socket.setSoTimeout(config.socketTimeout());
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }

    private void sendError(Socket socket, HTTPStatus status, String message) {
        try {
            HTTPResponse response = new HTTPResponse(socket.getOutputStream());
            response.status(status).json("{\"error\": \"" + message + "\"}");
        } catch (IOException ignored) {
        }
    }

    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanup() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket: " + e.getMessage());
            }
        }

        connectionPool.shutdown();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(config.shutdownTimeout(), TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorService createExecutor() {
        if (config.useVirtualThreads()) {
            try {
                return (ExecutorService) Executors.class
                        .getMethod("newVirtualThreadPerTaskExecutor")
                        .invoke(null);
            } catch (Exception e) {
                log("Virtual threads not available, using thread pool");
            }
        }

        int threads = config.workerThreads() > 0
                ? config.workerThreads()
                : Runtime.getRuntime().availableProcessors() * 2;

        return Executors.newFixedThreadPool(threads);
    }

    private void handleLog(PluginContext.LogMessage msg) {
        String level = msg.level().name();
        String message = String.format("[%s] %s", level, msg.message());

        if (msg.throwable() != null) {
            System.err.println(message);
            msg.throwable().printStackTrace();
        } else {
            System.out.println(message);
        }
    }

    private void log(String message) {
        System.out.println("[NOVA] " + message);
    }

    /**
     * Check if magic bytes indicate an HTTP request
     */
    private boolean isHttpRequest(byte[] magicBytes) {
        if (magicBytes.length < 4) return false;
        
        // Check for common HTTP methods
        // GET = 47 45 54 20
        // POST = 50 4F 53 54
        // PUT = 50 55 54 20
        // DELETE = 44 45 4C 45
        // PATCH = 50 41 54 43
        // HEAD = 48 45 41 44
        // OPTIONS = 4F 50 54 49
        
        // GET 
        if (magicBytes[0] == 'G' && magicBytes[1] == 'E' && magicBytes[2] == 'T' && magicBytes[3] == ' ')
            return true;
        // POST
        if (magicBytes[0] == 'P' && magicBytes[1] == 'O' && magicBytes[2] == 'S' && magicBytes[3] == 'T')
            return true;
        // PUT 
        if (magicBytes[0] == 'P' && magicBytes[1] == 'U' && magicBytes[2] == 'T' && magicBytes[3] == ' ')
            return true;
        // DELETE / DELET...
        if (magicBytes[0] == 'D' && magicBytes[1] == 'E' && magicBytes[2] == 'L' && magicBytes[3] == 'E')
            return true;
        // PATCH / PATC...
        if (magicBytes[0] == 'P' && magicBytes[1] == 'A' && magicBytes[2] == 'T' && magicBytes[3] == 'C')
            return true;
        // HEAD
        if (magicBytes[0] == 'H' && magicBytes[1] == 'E' && magicBytes[2] == 'A' && magicBytes[3] == 'D')
            return true;
        // OPTIONS / OPTI...
        if (magicBytes[0] == 'O' && magicBytes[1] == 'P' && magicBytes[2] == 'T' && magicBytes[3] == 'I')
            return true;
        
        return false;
    }

    // ========== GETTERS ==========

    public boolean isRunning() {
        return running.get();
    }

    public NovaConfig getConfig() {
        return config;
    }

    public PluginManager getPluginManager() {
        return pluginManager;
    }
}
