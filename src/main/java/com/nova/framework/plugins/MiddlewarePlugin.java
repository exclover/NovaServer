package com.nova.framework.plugins;

import com.nova.framework.http.HTTPRequest;
import com.nova.framework.http.HTTPResponse;
import com.nova.framework.plugin.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Middleware Plugin - DEFAULT
 * Provides request/response middleware support
 */
public final class MiddlewarePlugin extends BasePlugin {

    private final List<Middleware> middlewares = new ArrayList<>();

    @Override
    public String id() {
        return "middleware";
    }

    @Override
    public boolean isDefault() {
        return true; // Auto-registered
    }

    @Override
    public PluginPriority priority() {
        return PluginPriority.NORMAL; // Before routing
    }

    @Override
    public Set<String> dependencies() {
        return Set.of("routing"); // Needs routing to work
    }

    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        context.log("Middleware plugin initialized");
    }

    // ========== PUBLIC API ==========

    /**
     * Add middleware
     */
    public MiddlewarePlugin use(Middleware middleware) {
        middlewares.add(middleware);
        return this;
    }

    /**
     * Execute middlewares for a request
     */
    public boolean execute(HTTPRequest request, HTTPResponse response) {
        for (Middleware middleware : middlewares) {
            try {
                MiddlewareResult result = middleware.handle(request, response);
                if (result == MiddlewareResult.STOP) {
                    return false; // Stop processing
                }
            } catch (Exception e) {
                context.error("Middleware error", e);
                return false;
            }
        }
        return true; // Continue processing
    }

    /**
     * Middleware functional interface
     */
    @FunctionalInterface
    public interface Middleware {
        MiddlewareResult handle(HTTPRequest request, HTTPResponse response) throws Exception;
    }

    /**
     * Middleware result
     */
    public enum MiddlewareResult {
        CONTINUE, // Continue to next middleware/route
        STOP // Stop processing
    }
}
