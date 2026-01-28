package com.nova.framework.protocol;

import java.io.*;
import java.net.Socket;

public class ProtocolDetector {
    
    private static final int PEEK_SIZE = 16;
    
    public enum Protocol {
        HTTP, WEBSOCKET, CUSTOM, UNKNOWN
    }
    
    public static Result detect(Socket socket) {
        try {
            InputStream original = socket.getInputStream();
            PushbackInputStream pushback = new PushbackInputStream(original, PEEK_SIZE);
            
            byte[] buffer = new byte[PEEK_SIZE];
            int read = pushback.read(buffer, 0, PEEK_SIZE);
            
            if (read <= 0) {
                return new Result(Protocol.UNKNOWN, socket);
            }
            
            Protocol detected = detectProtocol(buffer, read);
            
            if (read > 0) {
                pushback.unread(buffer, 0, read);
            }
            
            Socket wrappedSocket = new SocketWrapper(socket, pushback);
            return new Result(detected, wrappedSocket);
            
        } catch (Exception e) {
            return new Result(Protocol.UNKNOWN, socket);
        }
    }
    
    private static Protocol detectProtocol(byte[] data, int length) {
        if (length < 3) {
            return Protocol.UNKNOWN;
        }
        
        String start = new String(data, 0, Math.min(length, 10), java.nio.charset.StandardCharsets.US_ASCII);
        
        if (start.startsWith("GET ") || start.startsWith("POST ") ||
            start.startsWith("PUT ") || start.startsWith("DELETE ") ||
            start.startsWith("PATCH ") || start.startsWith("HEAD ") ||
            start.startsWith("OPTIONS ")) {
            return Protocol.HTTP;
        }
        
        // Custom protocol magic bytes
        if (length >= 4 && data[0] == 0x4E && data[1] == 0x4F && 
            data[2] == 0x56 && data[3] == 0x41) { // "NOVA"
            return Protocol.CUSTOM;
        }
        
        return Protocol.UNKNOWN;
    }
    
    public static class Result {
        private final Protocol protocol;
        private final Socket socket;
        
        Result(Protocol protocol, Socket socket) {
            this.protocol = protocol;
            this.socket = socket;
        }
        
        public Protocol protocol() { return protocol; }
        public Socket socket() { return socket; }
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
