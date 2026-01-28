package com.nova.framework.protocol;

import java.io.IOException;
import java.net.Socket;

@FunctionalInterface
public interface CustomProtocolHandler {
    void handle(Socket socket) throws IOException;
}