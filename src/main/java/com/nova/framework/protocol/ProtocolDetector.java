package com.nova.framework.protocol;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ProtocolDetector {
    
    private static final int PEEK_SIZE = 16;
    
    public enum Protocol {
        HTTP, WEBSOCKET, CUSTOM, UNKNOWN
    }
    
    /**
     * Detect protocol with custom protocol handler support
     * 
     * @param socket Client socket
     * @param customHandlers Map of magic bytes (hex string) to handlers
     * @return Detection result with protocol, wrapped socket, and magic key if custom protocol
     */
    public static Result detect(Socket socket, Map<String, CustomProtocolHandler> customHandlers) {
        try {
            InputStream original = socket.getInputStream();
            PushbackInputStream pushback = new PushbackInputStream(original, PEEK_SIZE);
            
            byte[] buffer = new byte[PEEK_SIZE];
            int read = pushback.read(buffer, 0, PEEK_SIZE);
            
            if (read <= 0) {
                return new Result(Protocol.UNKNOWN, socket, null);
            }
            
            DetectionInfo detected = detectProtocol(buffer, read, customHandlers);
            
            if (read > 0) {
                pushback.unread(buffer, 0, read);
            }
            
            Socket wrappedSocket = new SocketWrapper(socket, pushback);
            return new Result(detected.protocol, wrappedSocket, detected.magicKey);
            
        } catch (Exception e) {
            return new Result(Protocol.UNKNOWN, socket, null);
        }
    }
    
    private static DetectionInfo detectProtocol(byte[] data, int length, Map<String, CustomProtocolHandler> customHandlers) {
        if (length < 3) {
            return new DetectionInfo(Protocol.UNKNOWN, null);
        }
        
        String start = new String(data, 0, Math.min(length, 10), java.nio.charset.StandardCharsets.US_ASCII);
        
        // Check for HTTP methods
        if (start.startsWith("GET ") || start.startsWith("POST ") ||
            start.startsWith("PUT ") || start.startsWith("DELETE ") ||
            start.startsWith("PATCH ") || start.startsWith("HEAD ") ||
            start.startsWith("OPTIONS ")) {
            return new DetectionInfo(Protocol.HTTP, null);
        }
        
        // Check for custom protocols
        if (customHandlers != null && !customHandlers.isEmpty()) {
            for (Map.Entry<String, CustomProtocolHandler> entry : customHandlers.entrySet()) {
                String magicHex = entry.getKey();
                if (matchesMagicBytes(data, length, magicHex)) {
                    return new DetectionInfo(Protocol.CUSTOM, magicHex);
                }
            }
        }
        
        return new DetectionInfo(Protocol.UNKNOWN, null);
    }
    
    /**
     * Check if data starts with magic bytes
     * 
     * @param data Received data
     * @param length Length of received data
     * @param magicHex Hex string of magic bytes (e.g., "4E4F5641" for "NOVA")
     * @return true if matches
     */
    private static boolean matchesMagicBytes(byte[] data, int length, String magicHex) {
        if (magicHex == null || magicHex.length() % 2 != 0) {
            return false;
        }
        
        int magicLength = magicHex.length() / 2;
        if (length < magicLength) {
            return false;
        }
        
        try {
            for (int i = 0; i < magicLength; i++) {
                String hexByte = magicHex.substring(i * 2, i * 2 + 2);
                int expectedByte = Integer.parseInt(hexByte, 16);
                if ((data[i] & 0xFF) != expectedByte) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Detection info with protocol and magic key
     */
    private static class DetectionInfo {
        final Protocol protocol;
        final String magicKey;
        
        DetectionInfo(Protocol protocol, String magicKey) {
            this.protocol = protocol;
            this.magicKey = magicKey;
        }
    }
    
    /**
     * Detection result
     */
    public static class Result {
        private final Protocol protocol;
        private final Socket socket;
        private final String magicKey;
        
        Result(Protocol protocol, Socket socket, String magicKey) {
            this.protocol = protocol;
            this.socket = socket;
            this.magicKey = magicKey;
        }
        
        public Protocol protocol() { return protocol; }
        public Socket socket() { return socket; }
        public String magicKey() { return magicKey; }
    }
    
    private static class SocketWrapper extends Socket {
        private final Socket client;
        private final InputStream inputStream;
        
        SocketWrapper(Socket client, InputStream inputStream) {
            this.client = client;
            this.inputStream = inputStream;
        }
        
        @Override
        public InputStream getInputStream() {
            return inputStream;
        }
        
        @Override
        public OutputStream getOutputStream() throws IOException {
            return client.getOutputStream();
        }
        
        @Override
        public void close() throws IOException {
            client.close();
        }
        
        @Override
        public java.net.InetAddress getInetAddress() {
            return client.getInetAddress();
        }
        
        @Override
        public boolean isClosed() {
            return client.isClosed();
        }
        
        @Override
        public void setSoTimeout(int timeout) throws java.net.SocketException {
            client.setSoTimeout(timeout);
        }
        
        @Override
        public void setTcpNoDelay(boolean on) throws java.net.SocketException {
            client.setTcpNoDelay(on);
        }
        
        @Override
        public void setKeepAlive(boolean on) throws java.net.SocketException {
            client.setKeepAlive(on);
        }
    }
}
