package com.nova.framework;

import com.nova.framework.config.ServerConfig;
import com.nova.framework.connection.ConnectionPool;
import com.nova.framework.core.Request;
import com.nova.framework.core.Response;
import com.nova.framework.core.RequestParser;
import com.nova.framework.middleware.MiddlewarePipeline;
import com.nova.framework.middleware.MiddlewareHandler;
import com.nova.framework.middleware.MiddlewareContext;
import com.nova.framework.routing.Router;
import com.nova.framework.routing.RouteHandler;
import com.nova.framework.websocket.WebSocketHandler;
import com.nova.framework.websocket.WSUpgrader;
import com.nova.framework.protocol.ProtocolDetector;
import com.nova.framework.protocol.CustomProtocolHandler;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * NOVA Framework - Modern High-Performance Web Server
 * 
 * @version 2.1.0 - Fixed shutdown hook leak and resource cleanup
 */
public class NovaServer {
    
    private final ServerConfig config;
    private final Router router;
    private final MiddlewarePipeline pipeline;
    private final ConnectionPool connectionPool;
    private final ServerMetrics metrics;
    
    private volatile ServerSocket serverSocket;
    private volatile ExecutorService acceptorExecutor;
    private volatile ExecutorService workerExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    
    private volatile BiConsumer<String, Throwable> logger;
    private final Map<String, CustomProtocolHandler> customProtocolHandlers = new ConcurrentHashMap<>();
    
    // FIX: Use AtomicReference to safely manage shutdown hook
    private final AtomicReference<Thread> shutdownHookRef = new AtomicReference<>(null);
    
    // Constructors
    public NovaServer(int port) {
        this(ServerConfig.builder().port(port).build());
    }
    
    public NovaServer(ServerConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Config cannot be null");
        }
        
        this.config = config;
        this.router = new Router();
        this.pipeline = new MiddlewarePipeline();
        this.connectionPool = new ConnectionPool(config.maxConnections());
        this.metrics = new ServerMetrics();
        this.logger = createDefaultLogger();
    }
    
    // ========== LIFECYCLE ==========
    
    public NovaServer start() throws IOException {
        return start(null);
    }
    
    public NovaServer start(Runnable callback) throws IOException {
        if (running.get()) {
            throw new IllegalStateException("Server already running");
        }
        
        try {
            // Create server socket
            serverSocket = createServerSocket();
            serverSocket.setReuseAddress(true);
            
            // Initialize thread pools
            initializeExecutors();
            
            running.set(true);
            shuttingDown.set(false);
            
            log(">> Nova Server starting on port " + config.port());
            log(">> Configuration: " + config.toString());
            log(">> Virtual Threads: " + config.useVirtualThreads());
            log(">> SSL Enabled: " + config.sslEnabled());
            
            // Register shutdown hook
            registerShutdownHook();
            
            if (callback != null) {
                safeExecute(callback, "Start callback error");
            }
            
            // Start accept loop
            acceptorExecutor.submit(this::acceptLoop);
            
            log(">> Server started successfully");
            
        } catch (IOException e) {
            running.set(false);
            cleanup();
            throw new IOException("Failed to start server", e);
        }
        
        return this;
    }
    
    public void stop() {
        if (!running.get() || shuttingDown.get()) {
            return;
        }
        
        log(">> Stopping server...");
        shuttingDown.set(true);
        running.set(false);
        
        // Remove shutdown hook
        removeShutdownHook();
        
        cleanup();
        
        log(">> Server stopped");
    }
    
    public void awaitTermination() {
        while (running.get()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }
    
    // ========== ROUTING API ==========
    
    public NovaServer get(String path, RouteHandler handler) {
        router.addRoute("GET", path, handler);
        return this;
    }
    
    public NovaServer post(String path, RouteHandler handler) {
        router.addRoute("POST", path, handler);
        return this;
    }
    
    public NovaServer put(String path, RouteHandler handler) {
        router.addRoute("PUT", path, handler);
        return this;
    }
    
    public NovaServer delete(String path, RouteHandler handler) {
        router.addRoute("DELETE", path, handler);
        return this;
    }
    
    public NovaServer patch(String path, RouteHandler handler) {
        router.addRoute("PATCH", path, handler);
        return this;
    }
    
    public NovaServer options(String path, RouteHandler handler) {
        router.addRoute("OPTIONS", path, handler);
        return this;
    }
    
    public NovaServer head(String path, RouteHandler handler) {
        router.addRoute("HEAD", path, handler);
        return this;
    }
    
    // ========== ROUTE GROUPING ==========
    
    public RouteGroup group(String prefix) {
        return new RouteGroup(this, prefix);
    }
    
    public static class RouteGroup {
        private final NovaServer server;
        private final String prefix;
        private final List<MiddlewareHandler> middlewares = new ArrayList<>();
        
        RouteGroup(NovaServer server, String prefix) {
            this.server = server;
            this.prefix = prefix != null ? prefix : "";
        }
        
        public RouteGroup get(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("GET", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup post(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("POST", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup put(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("PUT", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup delete(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("DELETE", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup patch(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("PATCH", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup options(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("OPTIONS", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup head(String path, RouteHandler handler) {
            String fullPath = prefix + path;
            server.router.addRoute("HEAD", fullPath, wrapWithMiddleware(handler));
            return this;
        }
        
        public RouteGroup use(MiddlewareHandler handler) {
            if (handler != null) {
                middlewares.add(handler);
            }
            return this;
        }
        
        public RouteGroup group(String subPrefix) {
            RouteGroup subGroup = new RouteGroup(server, prefix + (subPrefix != null ? subPrefix : ""));
            subGroup.middlewares.addAll(this.middlewares);
            return subGroup;
        }
        
        public NovaServer end() {
            return server;
        }
        
        private RouteHandler wrapWithMiddleware(RouteHandler handler) {
            if (middlewares.isEmpty()) {
                return handler;
            }
            
            return (req, res) -> {
                try {
                    for (MiddlewareHandler middleware : middlewares) {
                        MiddlewareContext ctx = new MiddlewareContext(req, res, server.router);
                        middleware.handle(ctx);
                        
                        if (ctx.isStopped() || res.isSent()) {
                            return;
                        }
                    }
                    
                    handler.handle(req, res);
                } catch (Exception e) {
                    server.log("Error in route group middleware", e);
                    if (!res.isSent()) {
                        res.status(500).json("{\"error\": \"Internal server error\"}");
                    }
                }
            };
        }
    }
    
    // ========== MIDDLEWARE API ==========
    
    public NovaServer use(MiddlewareHandler handler) {
        if (handler != null) {
            pipeline.add(handler);
        }
        return this;
    }
    
    public NovaServer use(String path, MiddlewareHandler handler) {
        if (handler != null) {
            pipeline.add(path, handler);
        }
        return this;
    }
    
    public NovaServer useBefore(MiddlewareHandler handler) {
        if (handler != null) {
            pipeline.addFirst(handler);
        }
        return this;
    }
    
    public NovaServer useAfter(MiddlewareHandler handler) {
        if (handler != null) {
            pipeline.addLast(handler);
        }
        return this;
    }
    
    // ========== WEBSOCKET API ==========
    
    public NovaServer websocket(String path, Consumer<WebSocketHandler> configure) {
        WebSocketHandler handler = new WebSocketHandler(path);
        if (configure != null) {
            configure.accept(handler);
        }
        router.addWebSocket(path, handler);
        return this;
    }
    
    // ========== PROTOCOL API ==========
    
    
    // ========== PROTOCOL API ==========
    
    /**
     * Register custom protocol handler with magic bytes
     * 
     * @param magicBytes Magic bytes to identify the protocol (e.g., new byte[]{0x4E, 0x4F, 0x56, 0x41} for "NOVA")
     * @param handler Handler for this protocol
     * @return this server instance
     */
    public NovaServer onCustomProtocol(byte[] magicBytes, CustomProtocolHandler handler) {
        if (magicBytes == null || magicBytes.length == 0) {
            throw new IllegalArgumentException("Magic bytes cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        String magicHex = bytesToHex(magicBytes);
        customProtocolHandlers.put(magicHex, handler);
        log("Registered custom protocol: " + magicHex);
        return this;
    }
    
    /**
     * Register custom protocol handler with hex string
     * 
     * @param magicHex Hex string of magic bytes (e.g., "4E4F5641" for "NOVA")
     * @param handler Handler for this protocol
     * @return this server instance
     */
    public NovaServer onCustomProtocol(String magicHex, CustomProtocolHandler handler) {
        if (magicHex == null || magicHex.isEmpty()) {
            throw new IllegalArgumentException("Magic hex cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        customProtocolHandlers.put(magicHex.toUpperCase(), handler);
        log("Registered custom protocol: " + magicHex);
        return this;
    }
    
    /**
     * Legacy method for backward compatibility
     * @deprecated Use onCustomProtocol(byte[], CustomProtocolHandler) instead
     */
    @Deprecated
    public NovaServer registerProtocol(CustomProtocolHandler handler) {
        return onCustomProtocol(new byte[]{0x4E, 0x4F, 0x56, 0x41}, handler);
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return hex.toString();
    }
    
    // ========== CONFIGURATION API ==========
    
    public NovaServer onLog(BiConsumer<String, Throwable> logger) {
        this.logger = logger != null ? logger : createDefaultLogger();
        return this;
    }
    
    public NovaServer enableCors() {
        return use((ctx) -> {
            ctx.response().setHeader("Access-Control-Allow-Origin", "*");
            ctx.response().setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
            ctx.response().setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
            ctx.next();
        });
    }
    
    public NovaServer enableCompression() {
        return use((ctx) -> {
            String acceptEncoding = ctx.request().header("Accept-Encoding");
            if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                ctx.response().enableCompression();
            }
            ctx.next();
        });
    }
    
    // ========== SHUTDOWN HOOK ==========
    
    /**
     * FIX: Properly manage shutdown hook to prevent memory leaks
     */
    private void registerShutdownHook() {
        Thread newHook = new Thread(() -> {
            if (running.get()) {
                log(">> Shutdown hook triggered");
                stop();
            }
        }, "nova-shutdown-hook");
        
        // Atomically set the hook
        Thread oldHook = shutdownHookRef.getAndSet(newHook);
        
        // Remove old hook if exists
        if (oldHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(oldHook);
            } catch (IllegalStateException e) {
                // Already shutting down or not registered
            }
        }
        
        // Register new hook
        try {
            Runtime.getRuntime().addShutdownHook(newHook);
        } catch (IllegalStateException e) {
            // Already shutting down
            shutdownHookRef.set(null);
        }
    }
    
    /**
     * FIX: Safely remove shutdown hook
     */
    private void removeShutdownHook() {
        Thread hook = shutdownHookRef.getAndSet(null);
        if (hook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException e) {
                // Already shutting down or hook not registered
            }
        }
    }
    
    // ========== INTERNAL: ACCEPT LOOP ==========
    
    private void acceptLoop() {
        log(">> Accept loop started");
        
        while (running.get() && !shuttingDown.get()) {
            Socket client = null;
            try {
                client = serverSocket.accept();
                
                if (shuttingDown.get()) {
                    closeSocket(client);
                    break;
                }
                
                if (!connectionPool.tryAcquire()) {
                    metrics.incrementRejectedConnections();
                    sendServiceUnavailable(client);
                    closeSocket(client);
                    continue;
                }
                
                metrics.incrementAcceptedConnections();
                
                final Socket finalClient = client;
                submitTask(() -> handleClient(finalClient));
                
            } catch (SocketException e) {
                if (running.get() && !shuttingDown.get()) {
                    log("Socket error in accept loop", e);
                }
            } catch (IOException e) {
                if (running.get() && !shuttingDown.get()) {
                    log("Accept error", e);
                }
            } catch (RejectedExecutionException e) {
                log("Worker pool saturated", e);
                metrics.incrementRejectedConnections();
                if (client != null) {
                    connectionPool.release();
                    sendServiceUnavailable(client);
                    closeSocket(client);
                }
            }
        }
        
        log(">> Accept loop stopped");
    }
    
    // ========== INTERNAL: CLIENT HANDLING ==========
    
    /**
     * FIX: Enhanced resource cleanup and error handling
     */
    private void handleClient(Socket client) {
        long startTime = System.nanoTime();
        InputStream inputStream = null;
        OutputStream outputStream = null;
        
        try {
            configureSocket(client);
            
            // Get streams early for proper cleanup
            inputStream = client.getInputStream();
            outputStream = client.getOutputStream();
            
            if (config.protocolDetection()) {
                handleWithProtocolDetection(client);
            } else {
                handleHTTP(client);
            }
            
        } catch (Exception e) {
            if (!isExpectedError(e)) {
                log("Client handling error", e);
                metrics.incrementErrors();
                
                // FIX: Attempt to send error response if possible
                if (outputStream != null) {
                    try {
                        sendError(client, 500, "Internal server error");
                    } catch (Exception ignored) {
                        // Can't send error response
                    }
                }
            }
        } finally {
            // FIX: Proper resource cleanup
            closeQuietly(inputStream);
            closeQuietly(outputStream);
            connectionPool.release();
            closeSocket(client);
            
            long duration = System.nanoTime() - startTime;
            metrics.recordRequestDuration(duration);
        }
    }
    
    private void handleWithProtocolDetection(Socket client) throws IOException {
        ProtocolDetector.Result result = ProtocolDetector.detect(client, customProtocolHandlers);
        
        switch (result.protocol()) {
            case HTTP -> handleHTTP(result.socket());
            case WEBSOCKET -> handleWebSocket(result.socket());
            case CUSTOM -> handleCustomProtocol(result.socket(), result.magicKey());
            case UNKNOWN -> {
                log("Unknown protocol detected from " + client.getInetAddress(), null);
                closeSocket(result.socket());
            }
        }
    }
    
    private void handleHTTP(Socket client) throws IOException {
        Request request = null;
        Response response = null;
        
        try {
            request = RequestParser.parse(
                client.getInputStream(),
                client.getInetAddress().getHostAddress(),
                config.maxRequestSize()
            );
            
            metrics.incrementRequests();
            
            if (request.isWebSocketUpgrade()) {
                handleWebSocketUpgrade(client, request);
                return;
            }
            
            response = new Response(client.getOutputStream());
            
            if ("OPTIONS".equals(request.getMethod())) {
                handleOptions(response);
                return;
            }
            
            MiddlewareContext context = new MiddlewareContext(request, response, router);
            pipeline.execute(context);
            
            if (!response.isSent()) {
                response.status(500).json("{\"error\": \"Handler did not send response\"}");
            }
            
        } catch (RequestParser.RequestTooLargeException e) {
            metrics.incrementErrors();
            sendError(client, 413, "Request too large");
        } catch (Exception e) {
            metrics.incrementErrors();
            log("HTTP handling error", e);
            if (response != null && !response.isSent()) {
                try {
                    response.status(500).json("{\"error\": \"Internal server error\"}");
                } catch (IOException ignored) {}
            }
        }
    }
    
    private void handleWebSocketUpgrade(Socket client, Request request) throws IOException {
        WebSocketHandler handler = router.findWebSocket(request.getPath());
        
        if (handler == null) {
            sendError(client, 404, "WebSocket endpoint not found");
            return;
        }
        
        WSUpgrader.upgrade(client, request, handler, workerExecutor, this::log);
    }
    
    private void handleWebSocket(Socket client) throws IOException {
        log("Direct WebSocket connection from " + client.getInetAddress(), null);
        closeSocket(client);
    }
    
    
    private void handleCustomProtocol(Socket client, String magicKey) {
        if (magicKey == null || !customProtocolHandlers.containsKey(magicKey)) {
            log("Custom protocol detected but no handler registered for magic: " + magicKey, null);
            closeSocket(client);
            return;
        }
        
        CustomProtocolHandler handler = customProtocolHandlers.get(magicKey);
        
        try {
            handler.handle(client);
        } catch (Exception e) {
            log("Custom protocol error for magic " + magicKey, e);
        }
    }
    
    // ========== INTERNAL: UTILITIES ==========
    
    private void initializeExecutors() {
        if (config.useVirtualThreads()) {
            try {
                acceptorExecutor = Executors.newVirtualThreadPerTaskExecutor();
                workerExecutor = Executors.newVirtualThreadPerTaskExecutor();
                log(">> Using Virtual Threads");
            } catch (Exception e) {
                log("!! Virtual threads not available, falling back to thread pool", null);
                initializeThreadPool();
            }
        } else {
            initializeThreadPool();
        }
    }
    
    private void initializeThreadPool() {
        int cores = Runtime.getRuntime().availableProcessors();
        int workers = Math.max(config.workerThreads(), cores * 2);
        
        acceptorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "nova-acceptor");
            t.setDaemon(false);
            return t;
        });
        
        workerExecutor = new ThreadPoolExecutor(
            workers / 2,
            workers,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(config.maxConnections()),
            new NovaThreadFactory("nova-worker"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        
        log(">> Using Thread Pool (workers: " + workers + ")");
    }
    
    private ServerSocket createServerSocket() throws IOException {
        if (config.sslEnabled()) {
            return createSSLServerSocket();
        }
        return new ServerSocket(config.port());
    }
    
    private ServerSocket createSSLServerSocket() throws IOException {
        try {
            SSLServerSocketFactory factory = config.sslConfig().createSSLContext()
                .getServerSocketFactory();
            
            SSLServerSocket sslSocket = (SSLServerSocket) factory.createServerSocket(config.port());
            sslSocket.setEnabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
            
            return sslSocket;
        } catch (Exception e) {
            throw new IOException("Failed to create SSL socket", e);
        }
    }
    
    private void configureSocket(Socket socket) throws IOException {
        if (socket == null || socket.isClosed()) {
            return;
        }
        
        socket.setSoTimeout(config.socketTimeout());
        socket.setTcpNoDelay(true);
        socket.setKeepAlive(true);
    }
    
    private void handleOptions(Response response) throws IOException {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.status(204).send("");
    }
    
    private void sendError(Socket socket, int status, String message) {
        try {
            String response = String.format(
                "HTTP/1.1 %d %s\r\n" +
                "Content-Type: application/json\r\n" +
                "Connection: close\r\n\r\n" +
                "{\"error\":\"%s\"}",
                status, getStatusText(status), escapeJson(message)
            );
            socket.getOutputStream().write(response.getBytes());
            socket.getOutputStream().flush();
        } catch (IOException ignored) {}
    }
    
    private void sendServiceUnavailable(Socket socket) {
        sendError(socket, 503, "Service unavailable - too many connections");
    }
    
    private String getStatusText(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 413 -> "Payload Too Large";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Unknown";
        };
    }
    
    private void submitTask(Runnable task) {
        if (workerExecutor != null && !workerExecutor.isShutdown()) {
            workerExecutor.submit(task);
        }
    }
    
    private void cleanup() {
        closeServerSocket();
        connectionPool.shutdown();
        shutdownExecutor(acceptorExecutor, "Acceptor");
        shutdownExecutor(workerExecutor, "Worker");
    }
    
    private void closeServerSocket() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log("Error closing server socket", e);
            }
        }
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            log("Shutting down " + name + " executor...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(config.shutdownTimeout(), TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log("!! " + name + " executor did not terminate", null);
                    }
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void closeSocket(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }
    
    /**
     * FIX: Safely close Closeable resources
     */
    private void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Ignore exceptions during cleanup
            }
        }
    }
    
    private boolean isExpectedError(Exception e) {
        if (e instanceof SocketException) {
            String msg = e.getMessage();
            return msg != null && (
                msg.contains("Connection reset") ||
                msg.contains("Broken pipe") ||
                msg.contains("Connection abort") ||
                msg.contains("Socket closed")
            );
        }
        return false;
    }
    
    private void safeExecute(Runnable runnable, String errorContext) {
        try {
            runnable.run();
        } catch (Exception e) {
            log(errorContext, e);
        }
    }
    
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    // ========== LOGGING ==========
    
    private BiConsumer<String, Throwable> createDefaultLogger() {
        return (msg, error) -> {
            if (error != null) {
                System.err.println("[NOVA ERROR] " + msg);
                error.printStackTrace();
            } else {
                System.out.println("[NOVA INFO] " + msg);
            }
        };
    }
    
    private void log(String message) {
        log(message, null);
    }
    
    private void log(String message, Throwable error) {
        BiConsumer<String, Throwable> currentLogger = logger;
        if (currentLogger != null) {
            currentLogger.accept(message, error);
        }
    }
    
    // ========== GETTERS ==========
    
    public boolean isRunning() {
        return running.get();
    }
    
    public int getPort() {
        return config.port();
    }
    
    public ServerMetrics getMetrics() {
        return metrics;
    }
    
    public String getStats() {
        return String.format(
            "{" +
                "\"port\": %d, " +
                "\"running\": %b, " +
                "\"connections\": %d, " +
                "\"routes\": %d, " +
                "\"websockets\": %d, " +
                "\"requests\": %d, " +
                "\"errors\": %d, " +
                "\"avgResponseTime\": %.2f" +
            "}",
            config.port(),
            running.get(),
            connectionPool.activeConnections(),
            router.routeCount(),
            router.websocketCount(),
            metrics.totalRequests(),
            metrics.totalErrors(),
            metrics.averageResponseTime()
        );
    }
    
    // ========== THREAD FACTORY ==========
    
    private static class NovaThreadFactory implements ThreadFactory {
        private final AtomicLong threadNumber = new AtomicLong(1);
        private final String namePrefix;
        
        NovaThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setPriority(Thread.NORM_PRIORITY);
            return thread;
        }
    }
    
    // ========== METRICS ==========
    
    public static class ServerMetrics {
        private final AtomicLong totalRequests = new AtomicLong(0);
        private final AtomicLong totalErrors = new AtomicLong(0);
        private final AtomicLong acceptedConnections = new AtomicLong(0);
        private final AtomicLong rejectedConnections = new AtomicLong(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        
        void incrementRequests() { totalRequests.incrementAndGet(); }
        void incrementErrors() { totalErrors.incrementAndGet(); }
        void incrementAcceptedConnections() { acceptedConnections.incrementAndGet(); }
        void incrementRejectedConnections() { rejectedConnections.incrementAndGet(); }
        
        void recordRequestDuration(long nanos) {
            totalResponseTime.addAndGet(nanos);
        }
        
        public long totalRequests() { return totalRequests.get(); }
        public long totalErrors() { return totalErrors.get(); }
        public long acceptedConnections() { return acceptedConnections.get(); }
        public long rejectedConnections() { return rejectedConnections.get(); }
        
        public double averageResponseTime() {
            long requests = totalRequests.get();
            return requests > 0 ? (totalResponseTime.get() / 1_000_000.0) / requests : 0;
        }
        
        public void reset() {
            totalRequests.set(0);
            totalErrors.set(0);
            acceptedConnections.set(0);
            rejectedConnections.set(0);
            totalResponseTime.set(0);
        }
    }
}
