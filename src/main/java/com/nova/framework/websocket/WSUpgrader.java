package com.nova.framework.websocket;

import com.nova.framework.core.Request;
import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

public class WSUpgrader {
    
    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    
    public static void upgrade(
        Socket socket,
        Request request,
        WebSocketHandler handler,
        ExecutorService executor,
        BiConsumer<String, Throwable> errorLogger
    ) {
        WSConnection conn = null;
        
        try {
            String key = request.header("Sec-WebSocket-Key");
            String acceptKey = computeAcceptKey(key);
            
            sendUpgradeResponse(socket.getOutputStream(), acceptKey);
            
            socket.setSoTimeout(0);
            socket.setKeepAlive(true);
            
            conn = new WSConnection(socket, request.clientIP(), request.routeParams());
            handler.addConnection(conn);
            
            if (handler.getOnConnect() != null) {
                handler.getOnConnect().accept(conn);
            }
            
            handleMessages(conn, handler, errorLogger);
            
        } catch (Exception e) {
            if (errorLogger != null) {
                errorLogger.accept("WebSocket upgrade error", e);
            }
        } finally {
            if (conn != null) {
                handler.removeConnection(conn);
                if (handler.getOnClose() != null) {
                    handler.getOnClose().accept(conn);
                }
            }
        }
    }
    
    private static void sendUpgradeResponse(OutputStream out, String acceptKey) throws IOException {
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                         "Upgrade: websocket\r\n" +
                         "Connection: Upgrade\r\n" +
                         "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        out.write(response.getBytes());
        out.flush();
    }
    
    private static String computeAcceptKey(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((key + MAGIC).getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    private static void handleMessages(
        WSConnection conn,
        WebSocketHandler handler,
        BiConsumer<String, Throwable> errorLogger
    ) {
        try {
            InputStream in = conn.socket.getInputStream();
            
            while (conn.isOpen()) {
                try {
                    WSFrame frame = WSFrame.read(in);
                    
                    if (frame == null) {
                        break;
                    }
                    
                    if (frame.isText()) {
                        if (handler.getOnMessage() != null) {
                            String message = frame.getTextPayload();
                            handler.getOnMessage().accept(conn, message);
                        }
                        
                    } else if (frame.isBinary()) {
                        if (handler.getOnBinary() != null) {
                            handler.getOnBinary().accept(conn, frame.getPayload());
                        }
                        
                    } else if (frame.isClose()) {
                        int statusCode = frame.getCloseStatusCode();
                        String reason = frame.getCloseReason();
                        
                        if (conn.isOpen()) {
                            conn.close(statusCode, reason);
                        }
                        break;
                        
                    } else if (frame.isPing()) {
                        try {
                            WSFrame.pong(frame.getPayload()).write(conn.socket.getOutputStream());
                        } catch (IOException e) {
                            if (errorLogger != null) {
                                errorLogger.accept("Failed to send pong", e);
                            }
                            break;
                        }
                        
                    } else if (frame.isPong()) {
                        // Pong received - keepalive confirmed
                        
                    } else if (frame.isContinuation()) {
                        if (errorLogger != null) {
                            errorLogger.accept(
                                "Received continuation frame",
                                new UnsupportedOperationException("Fragmented messages not yet supported")
                            );
                        }
                    }
                    
                } catch (IOException e) {
                    if (conn.isOpen() && errorLogger != null) {
                        String msg = e.getMessage();
                        if (msg == null || (!msg.contains("Connection reset") && 
                                           !msg.contains("Broken pipe") &&
                                           !msg.contains("Stream closed"))) {
                            errorLogger.accept("Frame read error", e);
                        }
                    }
                    break;
                }
            }
            
        } catch (Exception e) {
            if (errorLogger != null) {
                errorLogger.accept("Message loop error", e);
            }
        }
    }
}