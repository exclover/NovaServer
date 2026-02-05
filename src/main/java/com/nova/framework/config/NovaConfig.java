package com.nova.framework.config;

import java.util.concurrent.Executors;

/**
 * Immutable server configuration using Java record
 * 
 * @param port              Server port (1-65535)
 * @param maxConnections    Maximum concurrent connections
 * @param maxRequestSize    Maximum request size in bytes
 * @param socketTimeout     Socket timeout in milliseconds
 * @param shutdownTimeout   Shutdown timeout in seconds
 * @param workerThreads     Worker thread count (0 = auto)
 * @param useVirtualThreads Use virtual threads (Java 21+)
 */
public record NovaConfig(
        int port,
        int maxConnections,
        int maxRequestSize,
        int socketTimeout,
        int shutdownTimeout,
        int workerThreads,
        boolean useVirtualThreads) {

    // Default values
    private static final int DEFAULT_MAX_CONNECTIONS = 10000;
    private static final int DEFAULT_MAX_REQUEST_SIZE = 10 * 1024 * 1024; // 10MB
    private static final int DEFAULT_SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int DEFAULT_SHUTDOWN_TIMEOUT = 10; // 10 seconds
    private static final int DEFAULT_WORKER_THREADS = 0; // Auto

    /**
     * Compact constructor with validation
     */
    public NovaConfig {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 1-65535");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        if (maxRequestSize <= 0) {
            throw new IllegalArgumentException("Max request size must be positive");
        }
    }

    /**
     * Create builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NovaConfig
     */
    public static final class Builder {
        private int port = 8080;
        private int maxConnections = DEFAULT_MAX_CONNECTIONS;
        private int maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
        private int socketTimeout = DEFAULT_SOCKET_TIMEOUT;
        private int shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
        private int workerThreads = DEFAULT_WORKER_THREADS;
        private boolean useVirtualThreads = isVirtualThreadsAvailable();

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

        public NovaConfig build() {
            return new NovaConfig(
                    port,
                    maxConnections,
                    maxRequestSize,
                    socketTimeout,
                    shutdownTimeout,
                    workerThreads,
                    useVirtualThreads);
        }

        private static boolean isVirtualThreadsAvailable() {
            try {
                Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
                return true;
            } catch (NoSuchMethodException e) {
                return false;
            }
        }
    }
}
