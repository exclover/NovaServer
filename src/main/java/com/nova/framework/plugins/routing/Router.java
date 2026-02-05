package com.nova.framework.plugins.routing;

import com.nova.framework.http.HTTPMethod;
import com.nova.framework.http.HTTPRequest;
import com.nova.framework.http.HTTPResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple but efficient router
 * Stores routes and performs matching
 */
public final class Router {

    private final Map<HTTPMethod, List<Route>> routes = new ConcurrentHashMap<>();

    /**
     * Add a route
     */
    public void addRoute(HTTPMethod method, String path, RouteHandler handler) {
        routes.computeIfAbsent(method, k -> new ArrayList<>())
                .add(new Route(path, handler));
    }

    /**
     * Find matching route
     */
    public RouteMatch findRoute(HTTPMethod method, String path) {
        List<Route> methodRoutes = routes.get(method);
        if (methodRoutes == null)
            return null;

        for (Route route : methodRoutes) {
            Map<String, String> params = route.match(path);
            if (params != null) {
                return new RouteMatch(route.handler, params);
            }
        }

        return null;
    }

    /**
     * Handle request through router
     */
    public boolean handle(HTTPRequest request, HTTPResponse response) {
        RouteMatch match = findRoute(request.method(), request.path());

        if (match != null) {
            try {
                // Create new request with path params injected
                HTTPRequest requestWithParams = new HTTPRequest(
                        request.method(),
                        request.path(),
                        request.headers(),
                        request.body(),
                        request.queryParams(),
                        match.params, // Inject path params
                        request.clientAddress());

                match.handler.handle(requestWithParams, response);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        return false;
    }

    /**
     * Individual route
     */
    private static class Route {
        final String path;
        final RouteHandler handler;
        final Pattern pattern;
        final List<String> paramNames;
        final boolean isDynamic;

        Route(String path, RouteHandler handler) {
            this.path = path;
            this.handler = handler;
            this.paramNames = new ArrayList<>();
            this.isDynamic = path.contains(":");
            this.pattern = isDynamic ? compilePattern(path) : null;
        }

        Map<String, String> match(String requestPath) {
            if (!isDynamic) {
                return path.equals(requestPath) ? Map.of() : null;
            }

            Matcher matcher = pattern.matcher(requestPath);
            if (matcher.matches()) {
                Map<String, String> params = new HashMap<>();
                for (int i = 0; i < paramNames.size(); i++) {
                    params.put(paramNames.get(i), matcher.group(i + 1));
                }
                return params;
            }

            return null;
        }

        Pattern compilePattern(String path) {
            StringBuilder regex = new StringBuilder("^");
            String[] segments = path.split("/");

            for (String segment : segments) {
                if (segment.isEmpty())
                    continue;

                regex.append("/");

                if (segment.startsWith(":")) {
                    String paramName = segment.substring(1);
                    paramNames.add(paramName);
                    regex.append("([^/]+)");
                } else {
                    regex.append(Pattern.quote(segment));
                }
            }

            regex.append("$");
            return Pattern.compile(regex.toString());
        }
    }

    /**
     * Route match result
     */
    public record RouteMatch(RouteHandler handler, Map<String, String> params) {
    }
}
