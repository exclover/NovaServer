package com.nova.framework.middleware;

@FunctionalInterface
public interface MiddlewareHandler {
    void handle(MiddlewareContext context) throws Exception;
}