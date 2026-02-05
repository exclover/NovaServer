package com.nova.framework.plugins.routing;

import com.nova.framework.http.HTTPRequest;
import com.nova.framework.http.HTTPResponse;

import java.io.IOException;

/**
 * Route handler functional interface
 */
@FunctionalInterface
public interface RouteHandler {
    /**
     * Handle HTTP request
     */
    void handle(HTTPRequest request, HTTPResponse response) throws IOException;
}
