package com.nova.framework.middleware;

import com.nova.framework.core.Request;
import com.nova.framework.core.Response;
import com.nova.framework.routing.Router;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Middleware execution context
 * Thread-safe and provides state management for middleware chain
 */
public class MiddlewareContext {
    private final Request request;
    private final Response response;
    private final Router router;
    private volatile boolean stopped = false;
    private volatile int currentIndex = 0;
    
    // Context-specific attributes (thread-safe)
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    
    public MiddlewareContext(Request request, Response response, Router router) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (response == null) {
            throw new IllegalArgumentException("Response cannot be null");
        }
        if (router == null) {
            throw new IllegalArgumentException("Router cannot be null");
        }
        
        this.request = request;
        this.response = response;
        this.router = router;
    }
    
    // ========== CORE ACCESSORS ==========
    
    public Request request() { 
        return request; 
    }
    
    public Response response() { 
        return response; 
    }
    
    public Router router() { 
        return router; 
    }
    
    // ========== FLOW CONTROL ==========
    
    /**
     * Continue to next middleware in the pipeline
     */
    public void next() {
        stopped = false;
    }
    
    /**
     * Stop middleware execution chain
     * No subsequent middlewares will be executed
     */
    public void stop() {
        stopped = true;
    }
    
    /**
     * Check if middleware chain has been stopped
     */
    public boolean isStopped() {
        return stopped;
    }
    
    /**
     * Check if response has already been sent
     */
    public boolean isResponseSent() {
        return response.isSent();
    }
    
    // ========== CONTEXT ATTRIBUTES ==========
    
    /**
     * Set a context-specific attribute
     * Useful for passing data between middlewares
     * 
     * @param key Attribute key
     * @param value Attribute value (null removes the attribute)
     */
    public void set(String key, Object value) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Attribute key cannot be null or empty");
        }
        
        if (value == null) {
            attributes.remove(key);
        } else {
            attributes.put(key, value);
        }
    }
    
    /**
     * Get a context attribute
     * 
     * @param key Attribute key
     * @return Attribute value or null if not found
     */
    public Object get(String key) {
        return attributes.get(key);
    }
    
    /**
     * Get a typed context attribute
     * 
     * @param key Attribute key
     * @param type Expected type
     * @return Typed value or null if not found or wrong type
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }
    
    /**
     * Get attribute with default value
     */
    public <T> T getOrDefault(String key, T defaultValue) {
        @SuppressWarnings("unchecked")
        T value = (T) attributes.get(key);
        return value != null ? value : defaultValue;
    }
    
    /**
     * Check if attribute exists
     */
    public boolean has(String key) {
        return attributes.containsKey(key);
    }
    
    /**
     * Remove an attribute
     */
    public void remove(String key) {
        attributes.remove(key);
    }
    
    /**
     * Get all attributes (immutable copy)
     */
    public Map<String, Object> attributes() {
        return new HashMap<>(attributes);
    }
    
    /**
     * Clear all context attributes
     */
    public void clearAttributes() {
        attributes.clear();
    }
    
    // ========== INDEX MANAGEMENT (Package-private) ==========
    
    void setIndex(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("Index cannot be negative");
        }
        this.currentIndex = index;
    }
    
    int getIndex() {
        return currentIndex;
    }
    
    // ========== CONVENIENCE METHODS ==========
    
    /**
     * Get path parameter from request
     */
    public String param(String name) {
        return request.param(name);
    }
    
    /**
     * Get query parameter from request
     */
    public String query(String name) {
        return request.query(name);
    }
    
    /**
     * Get header from request
     */
    public String header(String name) {
        return request.header(name);
    }
    
    /**
     * Get cookie from request
     */
    public String cookie(String name) {
        return request.cookie(name);
    }
    
    /**
     * Get request path
     */
    public String path() {
        return request.path();
    }
    
    /**
     * Get request method
     */
    public String method() {
        return request.method();
    }
    
    /**
     * Get client IP address
     */
    public String clientIP() {
        return request.clientIP();
    }
    
    @Override
    public String toString() {
        return String.format(
            "MiddlewareContext{method=%s, path=%s, index=%d, stopped=%b, sent=%b}",
            request.method(),
            request.path(),
            currentIndex,
            stopped,
            response.isSent()
        );
    }
}