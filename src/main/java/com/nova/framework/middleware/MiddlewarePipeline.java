package com.nova.framework.middleware;

import com.nova.framework.routing.Router;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Middleware execution pipeline with thread-safe operations
 * Manages the chain of middleware handlers
 */
public class MiddlewarePipeline {
    // Thread-safe list for concurrent modifications
    private final List<MiddlewareEntry> middlewares = new CopyOnWriteArrayList<>();
    
    /**
     * Add middleware to the end of the pipeline
     */
    public void add(MiddlewareHandler handler) {
        add(null, handler);
    }
    
    /**
     * Add path-specific middleware
     * 
     * @param path Path prefix (null for all paths)
     * @param handler Middleware handler
     */
    public void add(String path, MiddlewareHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Middleware handler cannot be null");
        }
        middlewares.add(new MiddlewareEntry(path, handler));
    }
    
    /**
     * Add middleware at the beginning of the pipeline
     * Useful for authentication, logging, etc.
     */
    public void addFirst(MiddlewareHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Middleware handler cannot be null");
        }
        middlewares.add(0, new MiddlewareEntry(null, handler));
    }
    
    /**
     * Add middleware at the end of the pipeline
     * Useful for cleanup, error handling, etc.
     */
    public void addLast(MiddlewareHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Middleware handler cannot be null");
        }
        middlewares.add(new MiddlewareEntry(null, handler));
    }
    
    /**
     * Remove all middlewares
     */
    public void clear() {
        middlewares.clear();
    }
    
    /**
     * Execute the middleware pipeline
     * 
     * @param context Middleware context
     * @throws Exception if any middleware or route handler throws
     */
    public void execute(MiddlewareContext context) throws Exception {
        if (context == null) {
            throw new IllegalArgumentException("Middleware context cannot be null");
        }
        
        // Execute middleware chain
        for (int i = 0; i < middlewares.size(); i++) {
            MiddlewareEntry entry = middlewares.get(i);
            context.setIndex(i);
            
            // Check if middleware applies to this path
            if (!shouldExecuteMiddleware(entry, context)) {
                continue;
            }
            
            try {
                entry.handler.handle(context);
            } catch (Exception e) {
                // Log error and rethrow
                throw new MiddlewareExecutionException(
                    "Middleware execution failed at index " + i + 
                    " for path " + context.path(), 
                    e
                );
            }
            
            // Stop if middleware signaled stop
            if (context.isStopped()) {
                return;
            }
            
            // Stop if response already sent
            if (context.isResponseSent()) {
                return;
            }
        }
        
        // Execute route handler if no middleware sent response
        if (!context.isResponseSent()) {
            executeRoute(context);
        }
    }
    
    /**
     * Check if middleware should execute for the given context
     */
    private boolean shouldExecuteMiddleware(MiddlewareEntry entry, MiddlewareContext context) {
        if (entry.path == null) {
            return true; // Global middleware
        }
        
        String requestPath = context.path();
        if (requestPath == null) {
            return false;
        }
        
        // Check if request path starts with middleware path
        return requestPath.equals(entry.path) || 
               requestPath.startsWith(entry.path + "/");
    }
    
    /**
     * Execute the matched route handler
     */
    private void executeRoute(MiddlewareContext context) throws Exception {
        Router.RouteMatch match = context.router().findRoute(
            context.method(),
            context.path()
        );
        
        if (match != null) {
            // Set route params in request using setParams
            context.request().setParams(match.params());
            
            // Execute handler
            try {
                match.handler().handle(context.request(), context.response());
            } catch (IOException e) {
                throw new RouteExecutionException(
                    "Route handler failed for " + context.method() + " " + context.path(),
                    e
                );
            }
        } else {
            // 404 Not Found
            if (!context.isResponseSent()) {
                context.response()
                    .status(404)
                    .json("{\"error\": \"Not found\", \"path\": \"" + 
                          escapeJson(context.path()) + "\"}");
            }
        }
    }
    
    /**
     * Get number of registered middlewares
     */
    public int size() {
        return middlewares.size();
    }
    
    /**
     * Check if pipeline is empty
     */
    public boolean isEmpty() {
        return middlewares.isEmpty();
    }
    
    /**
     * Get middleware info (for debugging)
     */
    public List<String> getMiddlewareInfo() {
        List<String> info = new ArrayList<>();
        for (int i = 0; i < middlewares.size(); i++) {
            MiddlewareEntry entry = middlewares.get(i);
            String pathInfo = entry.path != null ? entry.path : "*";
            info.add(String.format("[%d] %s -> %s", i, pathInfo, entry.handler.getClass().getSimpleName()));
        }
        return info;
    }
    
    /**
     * Escape JSON string
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    // ========== MIDDLEWARE ENTRY ==========
    
    private static class MiddlewareEntry {
        final String path;
        final MiddlewareHandler handler;
        
        MiddlewareEntry(String path, MiddlewareHandler handler) {
            this.path = path;
            this.handler = handler;
        }
        
        @Override
        public String toString() {
            return String.format("MiddlewareEntry{path='%s', handler=%s}", 
                path != null ? path : "*", 
                handler.getClass().getSimpleName());
        }
    }
    
    // ========== CUSTOM EXCEPTIONS ==========
    
    /**
     * Exception thrown when middleware execution fails
     */
    public static class MiddlewareExecutionException extends Exception {
        public MiddlewareExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Exception thrown when route handler execution fails
     */
    public static class RouteExecutionException extends Exception {
        public RouteExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}