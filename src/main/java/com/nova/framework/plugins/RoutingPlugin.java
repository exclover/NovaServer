package com.nova.framework.plugins;

import com.nova.framework.http.HTTPMethod;
import com.nova.framework.plugin.*;
import com.nova.framework.plugins.routing.RouteHandler;
import com.nova.framework.plugins.routing.Router;

/**
 * Routing Plugin - DEFAULT
 * Provides HTTP routing functionality
 */
public final class RoutingPlugin extends BasePlugin {

    private Router router;

    @Override
    public String id() {
        return "routing";
    }

    @Override
    public boolean isDefault() {
        return true; // Auto-registered
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.BELOW_NORMAL; // After middleware
    }

    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        this.router = new Router();
        context.log("Routing plugin initialized");
    }

    @Override
    public void start() {
        context.log("Routing plugin started");
    }

    // ========== PUBLIC API ==========

    public Router getRouter() {
        return router;
    }

    public RoutingPlugin route(HTTPMethod method, String path, RouteHandler handler) {
        router.addRoute(method, path, handler);
        return this;
    }

    public RoutingPlugin get(String path, RouteHandler handler) {
        return route(HTTPMethod.GET, path, handler);
    }

    public RoutingPlugin post(String path, RouteHandler handler) {
        return route(HTTPMethod.POST, path, handler);
    }

    public RoutingPlugin put(String path, RouteHandler handler) {
        return route(HTTPMethod.PUT, path, handler);
    }

    public RoutingPlugin delete(String path, RouteHandler handler) {
        return route(HTTPMethod.DELETE, path, handler);
    }
}
