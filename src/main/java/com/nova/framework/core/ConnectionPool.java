package com.nova.framework.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Connection pool for managing concurrent connections
 */
public final class ConnectionPool {

    private final int maxConnections;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public ConnectionPool(int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        this.maxConnections = maxConnections;
    }

    /**
     * Try to acquire a connection slot
     * 
     * @return true if acquired, false if pool is full
     */
    public boolean tryAcquire() {
        int current;
        do {
            current = activeConnections.get();
            if (current >= maxConnections) {
                return false;
            }
        } while (!activeConnections.compareAndSet(current, current + 1));

        return true;
    }

    /**
     * Release a connection slot
     */
    public void release() {
        int current;
        do {
            current = activeConnections.get();
            if (current <= 0) {
                // Prevent negative counts - this indicates a bug but we handle it gracefully
                System.err.println("[WARNING] ConnectionPool: Attempted to release when count is " + current);
                return;
            }
        } while (!activeConnections.compareAndSet(current, current - 1));
    }

    /**
     * Get active connection count
     */
    public int activeCount() {
        return activeConnections.get();
    }

    /**
     * Get max connections
     */
    public int maxConnections() {
        return maxConnections;
    }

    /**
     * Check if pool is full
     */
    public boolean isFull() {
        return activeConnections.get() >= maxConnections;
    }

    /**
     * Shutdown pool
     */
    public void shutdown() {
        // Reset connection count
        activeConnections.set(0);
    }
}
