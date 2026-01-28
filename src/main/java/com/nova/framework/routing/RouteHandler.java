package com.nova.framework.routing;

import com.nova.framework.core.Request;
import com.nova.framework.core.Response;
import java.io.IOException;

@FunctionalInterface
public interface RouteHandler {
    void handle(Request request, Response response) throws IOException;
}
