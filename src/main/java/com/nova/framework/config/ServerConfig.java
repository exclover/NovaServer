package com.nova.framework.config;

import com.nova.framework.ssl.SSLConfig;

/**
 * Server configuration with builder pattern
 * Immutable after creation
 */
public record ServerConfig(
    int port,
    int maxConnections,
    int maxRequestSize,
    int socketTimeout,
    int shutdownTimeout,
    int workerThreads,
    boolean useVirtualThreads,
    boolean protocolDetection,
    boolean sslEnabled,
    SSLConfig sslConfig,
    boolean hotReload,
    boolean compressionEnabled,
    boolean corsEnabled
) {
    
    // Default values
    private static final int DEFAULT_MAX_CONNECTIONS = 10000;
    private static final int DEFAULT_MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 10; // 10 seconds
    private static final int DEFAULT_WORKER_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    
    public ServerConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1-65535");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        if (maxRequestSize <= 0) {
            throw new IllegalArgumentException("Max request size must be positive");
        }
        if (sslEnabled && sslConfig == null) {
            throw new IllegalArgumentException("SSL config required when SSL is enabled");
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    @Override
    public String toString() {
        return String.format(
            "ServerConfig{port=%d, maxConn=%d, maxReqSize=%dMB, ssl=%b, vThreads=%b}",
            port, maxConnections, maxRequestSize / (1024 * 1024), sslEnabled, useVirtualThreads
        );
    }
    
    public static class Builder {
        private int port;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private int maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
        private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        private int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private int workerThreads = DEFAULT_WORKER_THREADS;
        private boolean useVirtualThreads = isVirtualThreadsAvailable();
        private boolean protocolDetection = true;
        private boolean sslEnabled = false;
        private SSLConfig sslConfig = null;
        private boolean hotReload = false;
        private boolean compressionEnabled = false;
        private boolean corsEnabled = false;
        
        public Builder port(int port) {
            this.port = port;
            return this;
        }
        
        public Builder maxConnections(int max) {
            this.maxConnections = max;
            return this;
        }
        
        public Builder maxRequestSize(int bytes) {
            this.maxRequestSize = bytes;
            return this;
        }
        
        public Builder socketTimeout(int millis) {
            this.socketTimeout = millis;
            return this;
        }
        
        public Builder shutdownTimeout(int seconds) {
            this.shutdownTimeout = seconds;
            return this;
        }
        
        public Builder workerThreads(int threads) {
            this.workerThreads = threads;
            return this;
        }
        
        public Builder useVirtualThreads(boolean use) {
            this.useVirtualThreads = use;
            return this;
        }
        
        public Builder protocolDetection(boolean enable) {
            this.protocolDetection = enable;
            return this;
        }
        
        public Builder enableSSL(SSLConfig config) {
            this.sslEnabled = true;
            this.sslConfig = config;
            return this;
        }
        
        public Builder hotReload(boolean enable) {
            this.hotReload = enable;
            return this;
        }
        
        public Builder compression(boolean enable) {
            this.compressionEnabled = enable;
            return this;
        }
        
        public Builder cors(boolean enable) {
            this.corsEnabled = enable;
            return this;
        }
        
        public ServerConfig build() {
            return new ServerConfig(
                port,
                maxConnections,
                maxRequestSize,
                socketTimeout,
                shutdownTimeout,
                workerThreads,
                useVirtualThreads,
                protocolDetection,
                sslEnabled,
                sslConfig,
                hotReload,
                compressionEnabled,
                corsEnabled
            );
        }
        
        private static boolean isVirtualThreadsAvailable() {
            try {
                Class.forName("java.lang.Thread$Builder");
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }
}