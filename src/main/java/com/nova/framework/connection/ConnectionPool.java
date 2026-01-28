package com.nova.framework.connection;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe connection pool with fixed race conditions
 * 
 * @version 2.1.0 - Fixed race condition in release()
 */
public class ConnectionPool {
    private final Semaphore semaphore;
    private final AtomicInteger active = new AtomicInteger(0);
    private final int maxConnections;
    
    public ConnectionPool(int maxConnections) {
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        this.maxConnections = maxConnections;
        this.semaphore = new Semaphore(maxConnections, true);
    }
    
    /**
     * Try to acquire a connection permit
     * Thread-safe operation with atomic counter update
     * 
     * @return true if permit acquired, false if pool is full
     */
    public boolean tryAcquire() {
        boolean acquired = semaphore.tryAcquire();
        if (acquired) {
            active.incrementAndGet();
        }
        return acquired;
    }
    
    /**
     * Release a connection permit
     * Thread-safe operation - uses atomic update to prevent race condition
     */
    public void release() {
        // FIX: Use getAndUpdate to make check-and-decrement atomic
        int previousValue = active.getAndUpdate(v -> v > 0 ? v - 1 : v);
        
        if (previousValue > 0) {
            semaphore.release();
        } else {
            // Edge case: release called without acquire
            // Still release semaphore to prevent deadlock, but log warning
            System.err.println("WARNING: ConnectionPool.release() called without matching acquire()");
            semaphore.release();
        }
    }
    
    /**
     * Get current number of active connections
     */
    public int activeConnections() {
        return active.get();
    }
    
    /**
     * Get number of available connection slots
     */
    public int availableConnections() {
        return semaphore.availablePermits();
    }
    
    /**
     * Get maximum allowed connections
     */
    public int maxConnections() {
        return maxConnections;
    }
    
    /**
     * Get pool utilization percentage (0-100)
     */
    public double utilizationPercent() {
        return (active.get() * 100.0) / maxConnections;
    }
    
    /**
     * Check if pool is at capacity
     */
    public boolean isFull() {
        return availableConnections() == 0;
    }
    
    /**
     * Check if pool is empty
     */
    public boolean isEmpty() {
        return active.get() == 0;
    }
    
    /**
     * Shutdown the pool - drain all permits and reset counter
     */
    public void shutdown() {
        semaphore.drainPermits();
        active.set(0);
    }
    
    /**
     * Reset the pool to initial state
     * Useful for testing or recovery scenarios
     */
    public void reset() {
        shutdown();
        // Restore all permits
        semaphore.release(maxConnections);
    }
    
    @Override
    public String toString() {
        return String.format(
            "ConnectionPool{max=%d, active=%d, available=%d, utilization=%.1f%%}",
            maxConnections,
            active.get(),
            availableConnections(),
            utilizationPercent()
        );
    }
}