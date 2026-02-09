package com.nova.framework.plugins.routing;

import com.nova.framework.http.HTTPMethod;
import com.nova.framework.http.HTTPRequest;
import com.nova.framework.http.HTTPResponse;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Router {

    // Statik rotalar için direkt erişim (Yüksek Performans)
    private final Map<HTTPMethod, Map<String, Route>> staticRoutes = new ConcurrentHashMap<>();
    // Dinamik rotalar için liste
    private final Map<HTTPMethod, List<Route>> dynamicRoutes = new ConcurrentHashMap<>();

    public void addRoute(HTTPMethod method, String path, RouteHandler handler) {
        Route route = new Route(path, handler);
        if (route.isDynamic) {
            dynamicRoutes.computeIfAbsent(method, k -> new ArrayList<>()).add(route);
        } else {
            staticRoutes.computeIfAbsent(method, k -> new HashMap<>()).put(path, route);
        }
    }

    public RouteMatch findRoute(HTTPMethod method, String path) {
        // 1. Önce statik rotalara bak (O(1) hızında)
        Map<String, Route> methodStatic = staticRoutes.get(method);
        if (methodStatic != null && methodStatic.containsKey(path)) {
            return new RouteMatch(methodStatic.get(path).handler, Map.of());
        }

        // 2. Bulunamazsa dinamik rotaları tara
        List<Route> methodDynamic = dynamicRoutes.get(method);
        if (methodDynamic != null) {
            for (Route route : methodDynamic) {
                Map<String, String> params = route.match(path);
                if (params != null) {
                    return new RouteMatch(route.handler, params);
                }
            }
        }
        return null;
    }

    public boolean handle(HTTPRequest request, HTTPResponse response) {
        RouteMatch match = findRoute(request.method(), request.path());
        
        if (match == null) return false;

        try {
            // Context nesnesi kullanmak (Request/Response sarmalayıcı) daha profosyoneldir
            // Şimdilik mevcut yapını koruyarak parametreleri enjekte ediyoruz
            match.handler.handle(injectParams(request, match.params), response);
            return true;
        } catch (Exception e) {
            // Burada bir ErrorHandler tetiklenebilir
            return false;
        }
    }

    private HTTPRequest injectParams(HTTPRequest req, Map<String, String> params) {
        return new HTTPRequest(req.method(), req.path(), req.headers(), 
                               req.body(), req.queryParams(), params, req.clientAddress());
    }

    private static class Route {
        final RouteHandler handler;
        final Pattern pattern;
        final List<String> paramNames = new ArrayList<>();
        final boolean isDynamic;

        Route(String path, RouteHandler handler) {
            this.handler = handler;
            this.isDynamic = path.contains(":") || path.contains("*");
            this.pattern = isDynamic ? compile(path) : null;
        }

        private Pattern compile(String path) {
            StringBuilder regex = new StringBuilder("^");
            // Path'i segmentlere ayırırken slash'ları koruyoruz
            String[] segments = path.split("/");

            for (String segment : segments) {
                if (segment.isEmpty()) continue;
                regex.append("/");

                if (segment.startsWith("*")) {
                    // Wildcard: /assets/*path
                    paramNames.add(segment.substring(1));
                    regex.append("(.*)");
                    break;
                } else if (segment.startsWith(":")) {
                    // Kısıtlamalı Parametre: :id<[0-9]+>
                    if (segment.contains("<") && segment.endsWith(">")) {
                        String paramName = segment.substring(1, segment.indexOf("<"));
                        String constraint = segment.substring(segment.indexOf("<") + 1, segment.length() - 1);
                        paramNames.add(paramName);
                        regex.append("(").append(constraint).append(")");
                    } else {
                        // Standart Parametre: :id
                        paramNames.add(segment.substring(1));
                        regex.append("([^/]+)");
                    }
                } else {
                    // Statik metin
                    regex.append(Pattern.quote(segment));
                }
            }
            
            if (path.endsWith("/")) regex.append("/");
            regex.append("$");
            return Pattern.compile(regex.toString());
        }

        Map<String, String> match(String requestPath) {
            Matcher m = pattern.matcher(requestPath);
            if (!m.matches()) return null;
            Map<String, String> params = new HashMap<>();
            for (int i = 0; i < paramNames.size(); i++) {
                params.put(paramNames.get(i), m.group(i + 1));
            }
            return params;
        }
    }

    public record RouteMatch(RouteHandler handler, Map<String, String> params) {}
}
