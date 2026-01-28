package com.nova.framework.routing;

import com.nova.framework.websocket.WebSocketHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced router with optimized route matching
 * 
 * @version 2.1.0 - Fixed performance issues with lazy sorting
 */
public class Router {
    
    private final Map<String, RouteList> routes = new ConcurrentHashMap<>();
    private final Map<String, WebSocketHandler> websockets = new ConcurrentHashMap<>();
    private final List<RouteGroup> groups = new ArrayList<>();
    
    private static final String[] SUPPORTED_METHODS = {
        "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD", "TRACE", "CONNECT"
    };
    
    public Router() {
        for (String method : SUPPORTED_METHODS) {
            routes.put(method, new RouteList());
        }
    }
    
    // ========== ROUTE REGISTRATION ==========
    
    /**
     * Add a route with string path
     */
    public void addRoute(String method, String path, RouteHandler handler) {
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("Method cannot be null or empty");
        }
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Path cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        String normalizedMethod = method.toUpperCase().trim();
        RouteList methodRoutes = routes.get(normalizedMethod);
        
        if (methodRoutes == null) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        String normalizedPath = normalizePath(path);
        Route route = new Route(normalizedPath, handler);
        
        methodRoutes.add(route);
    }
    
    /**
     * Add a route with regex pattern
     */
    public void addRoute(String method, Pattern pattern, RouteHandler handler) {
        if (method == null || method.trim().isEmpty()) {
            throw new IllegalArgumentException("Method cannot be null or empty");
        }
        if (pattern == null) {
            throw new IllegalArgumentException("Pattern cannot be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        
        String normalizedMethod = method.toUpperCase().trim();
        RouteList methodRoutes = routes.get(normalizedMethod);
        
        if (methodRoutes == null) {
            throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        
        Route route = new Route(pattern, handler);
        methodRoutes.add(route);
    }
    
    // ========== WEBSOCKET REGISTRATION ==========
    
    public void addWebSocket(String path, WebSocketHandler handler) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("WebSocket path cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("WebSocket handler cannot be null");
        }
        
        String normalizedPath = normalizePath(path);
        websockets.put(normalizedPath, handler);
    }
    
    public WebSocketHandler findWebSocket(String path) {
        if (path == null) {
            return null;
        }
        return websockets.get(normalizePath(path));
    }
    
    public boolean removeWebSocket(String path) {
        if (path == null) {
            return false;
        }
        return websockets.remove(normalizePath(path)) != null;
    }
    
    // ========== ROUTE MATCHING ==========
    
    /**
     * Find matching route for method and path
     * FIX: Uses lazy-sorted list for better performance
     */
    public RouteMatch findRoute(String method, String path) {
        if (method == null || path == null) {
            return null;
        }
        
        String normalizedMethod = method.toUpperCase().trim();
        RouteList methodRoutes = routes.get(normalizedMethod);
        
        if (methodRoutes == null) {
            return null;
        }
        
        String normalizedPath = normalizePath(path);
        return methodRoutes.findMatch(normalizedPath);
    }
    
    // ========== ROUTE GROUPS ==========
    
    public RouteGroup group(String prefix) {
        RouteGroup group = new RouteGroup(this, prefix);
        groups.add(group);
        return group;
    }
    
    // ========== STATISTICS ==========
    
    public int routeCount() {
        return routes.values().stream()
            .mapToInt(RouteList::size)
            .sum();
    }
    
    public int routeCount(String method) {
        if (method == null) {
            return 0;
        }
        
        RouteList methodRoutes = routes.get(method.toUpperCase().trim());
        return methodRoutes != null ? methodRoutes.size() : 0;
    }
    
    public int websocketCount() {
        return websockets.size();
    }
    
    public Set<String> getRegisteredMethods() {
        Set<String> methods = new HashSet<>();
        routes.forEach((method, routeList) -> {
            if (!routeList.isEmpty()) {
                methods.add(method);
            }
        });
        return methods;
    }
    
    public void clearRoutes() {
        routes.values().forEach(RouteList::clear);
    }
    
    public void clearWebSockets() {
        websockets.clear();
    }
    
    public void clearAll() {
        clearRoutes();
        clearWebSockets();
        groups.clear();
    }
    
    // ========== UTILITIES ==========
    
    private String normalizePath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return "/";
        }
        
        path = path.trim();
        
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        return path;
    }
    
    // ========== ROUTE LIST WITH LAZY SORTING ==========
    
    /**
     * FIX: Optimized route list that sorts lazily
     * Only sorts when routes are added, not on every lookup
     */
    private static class RouteList {
        private final List<Route> routes = new ArrayList<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();
        private volatile boolean needsSort = false;
        
        void add(Route route) {
            lock.writeLock().lock();
            try {
                routes.add(route);
                needsSort = true;
            } finally {
                lock.writeLock().unlock();
            }
        }
        
        RouteMatch findMatch(String path) {
            lock.readLock().lock();
            try {
                // Sort if needed (happens once after batch adds)
                if (needsSort) {
                    // Upgrade to write lock
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        if (needsSort) { // Double-check
                            routes.sort((a, b) -> Integer.compare(b.specificity(), a.specificity()));
                            needsSort = false;
                        }
                        // Downgrade to read lock
                        lock.readLock().lock();
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
                
                // Find match in sorted list
                for (Route route : routes) {
                    RouteMatch match = route.match(path);
                    if (match != null) {
                        return match;
                    }
                }
                
                return null;
            } finally {
                lock.readLock().unlock();
            }
        }
        
        int size() {
            lock.readLock().lock();
            try {
                return routes.size();
            } finally {
                lock.readLock().unlock();
            }
        }
        
        boolean isEmpty() {
            return size() == 0;
        }
        
        void clear() {
            lock.writeLock().lock();
            try {
                routes.clear();
                needsSort = false;
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    // ========== ROUTE CLASS ==========
    
    private static class Route {
        private final String path;
        private final Pattern pattern;
        private final RouteHandler handler;
        private final List<String> paramNames;
        private final boolean isPattern;
        private final boolean isWildcard;
        private final int specificity;
        
        Route(String path, RouteHandler handler) {
            this.path = path;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            this.isWildcard = path.contains("*");
            
            if (path.contains(":") || isWildcard) {
                this.pattern = compilePattern(path);
                this.isPattern = true;
            } else {
                this.pattern = null;
                this.isPattern = false;
            }
            
            this.specificity = calculateSpecificity();
        }
        
        Route(Pattern pattern, RouteHandler handler) {
            this.path = null;
            this.pattern = pattern;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            this.isPattern = true;
            this.isWildcard = false;
            this.specificity = 100;
        }
        
        RouteMatch match(String requestPath) {
            if (requestPath == null) {
                return null;
            }
            
            if (!isPattern) {
                if (path.equals(requestPath)) {
                    return new RouteMatch(handler, new HashMap<>());
                }
                return null;
            }
            
            Matcher matcher = pattern.matcher(requestPath);
            if (matcher.matches()) {
                Map<String, String> params = new HashMap<>();
                
                int groupCount = Math.min(paramNames.size(), matcher.groupCount());
                for (int i = 0; i < groupCount; i++) {
                    String value = matcher.group(i + 1);
                    if (value != null) {
                        params.put(paramNames.get(i), value);
                    }
                }
                
                return new RouteMatch(handler, params);
            }
            
            return null;
        }
        
        int specificity() {
            return specificity;
        }
        
        private int calculateSpecificity() {
            if (!isPattern) {
                return 1000 + path.length();
            }
            
            if (isWildcard) {
                return 1;
            }
            
            int baseScore = 500;
            int paramPenalty = paramNames.size() * 10;
            int lengthBonus = path != null ? path.length() : 0;
            
            return baseScore - paramPenalty + lengthBonus;
        }
        
        private Pattern compilePattern(String path) {
            StringBuilder regex = new StringBuilder("^");
            String[] segments = path.split("/");
            
            for (String segment : segments) {
                if (segment.isEmpty()) {
                    continue;
                }
                
                regex.append("/");
                
                if (segment.startsWith(":")) {
                    String paramName = segment.substring(1);
                    
                    int openParen = paramName.indexOf('(');
                    if (openParen > 0) {
                        String name = paramName.substring(0, openParen);
                        String constraint = paramName.substring(openParen);
                        paramNames.add(name);
                        regex.append(constraint);
                    } else {
                        paramNames.add(paramName);
                        regex.append("([^/]+)");
                    }
                    
                } else if (segment.equals("*")) {
                    regex.append(".*");
                    
                } else if (segment.equals("**")) {
                    regex.append(".*");
                    
                } else {
                    regex.append(Pattern.quote(segment));
                }
            }
            
            regex.append("$");
            
            try {
                return Pattern.compile(regex.toString());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid path pattern: " + path, e);
            }
        }
        
        @Override
        public String toString() {
            return String.format("Route{path='%s', pattern=%s, specificity=%d}", 
                path, isPattern, specificity);
        }
    }
    
    // ========== ROUTE MATCH ==========
    
    public static class RouteMatch {
        private final RouteHandler handler;
        private final Map<String, String> params;
        
        RouteMatch(RouteHandler handler, Map<String, String> params) {
            this.handler = handler;
            this.params = Collections.unmodifiableMap(params);
        }
        
        public RouteHandler handler() { 
            return handler; 
        }
        
        public Map<String, String> params() { 
            return params; 
        }
        
        @Override
        public String toString() {
            return String.format("RouteMatch{handler=%s, params=%s}", 
                handler.getClass().getSimpleName(), params);
        }
    }
    
    // ========== ROUTE GROUP ==========
    
    public static class RouteGroup {
        private final Router router;
        private final String prefix;
        private final List<String> middlewares = new ArrayList<>();
        
        RouteGroup(Router router, String prefix) {
            this.router = router;
            this.prefix = prefix != null ? prefix : "";
        }
        
        public RouteGroup get(String path, RouteHandler handler) {
            router.addRoute("GET", prefix + path, handler);
            return this;
        }
        
        public RouteGroup post(String path, RouteHandler handler) {
            router.addRoute("POST", prefix + path, handler);
            return this;
        }
        
        public RouteGroup put(String path, RouteHandler handler) {
            router.addRoute("PUT", prefix + path, handler);
            return this;
        }
        
        public RouteGroup delete(String path, RouteHandler handler) {
            router.addRoute("DELETE", prefix + path, handler);
            return this;
        }
        
        public RouteGroup patch(String path, RouteHandler handler) {
            router.addRoute("PATCH", prefix + path, handler);
            return this;
        }
        
        public RouteGroup options(String path, RouteHandler handler) {
            router.addRoute("OPTIONS", prefix + path, handler);
            return this;
        }
        
        public RouteGroup head(String path, RouteHandler handler) {
            router.addRoute("HEAD", prefix + path, handler);
            return this;
        }
        
        public RouteGroup middleware(String name) {
            if (name != null && !name.trim().isEmpty()) {
                middlewares.add(name);
            }
            return this;
        }
        
        public RouteGroup group(String subPrefix) {
            return new RouteGroup(router, prefix + (subPrefix != null ? subPrefix : ""));
        }
        
        public String getPrefix() {
            return prefix;
        }
        
        public List<String> getMiddlewares() {
            return Collections.unmodifiableList(middlewares);
        }
    }
}