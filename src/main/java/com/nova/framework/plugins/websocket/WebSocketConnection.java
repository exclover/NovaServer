package com.nova.framework.plugins.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * WebSocket client connection
 * Handles WebSocket framing protocol
 */
public final class WebSocketConnection {

    private final Socket socket;
    private final InputStream input;
    private final OutputStream output;
    private final String id;

    private Consumer<String> onMessage;
    private Consumer<CloseReason> onClose;
    private Runnable onCleanup; // Called when connection closes for resource cleanup
    private volatile boolean closed = false;

    public WebSocketConnection(Socket socket, String id) throws IOException {
        this.socket = socket;
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
        this.id = id;
    }

    public String getID() {
        return id;
    }

    public String getClientIP() {
        return socket.getInetAddress().getHostAddress();
    }

    public void onMessage(Consumer<String> handler) {
        this.onMessage = handler;
    }

    public void onClose(Consumer<CloseReason> handler) {
        this.onClose = handler;
    }

    /**
     * Set cleanup handler (internal use - for releasing connection pool)
     */
    public void onCleanup(Runnable cleanup) {
        this.onCleanup = cleanup;
    }

    public void send(String message) throws IOException {
        if (closed)
            return;

        synchronized (output) {
            byte[] payload = message.getBytes(StandardCharsets.UTF_8);

            // Simple text frame (FIN=1, opcode=0x1)
            output.write(0x81);

            // Length
            if (payload.length < 126) {
                output.write(payload.length);
            } else if (payload.length < 65536) {
                output.write(126);
                output.write((payload.length >> 8) & 0xFF);
                output.write(payload.length & 0xFF);
            } else {
                output.write(127);
                for (int i = 7; i >= 0; i--) {
                    output.write((payload.length >> (i * 8)) & 0xFF);
                }
            }

            output.write(payload);
            output.flush();
        }
    }

    public void close() throws IOException {
        if (closed)
            return;
        
        closed = true;
        
        try {
            // Send close frame
            synchronized (output) {
                output.write(0x88); // FIN=1, opcode=0x8 (close)
                output.write(0x00); // No payload
                output.flush();
            }
        } catch (IOException ignored) {
            // Socket might already be closed
        }
        
        // Close socket
        try {
            socket.close();
        } catch (IOException ignored) {
        }

        // Notify close handler
        if (onClose != null) {
            try {
                onClose.accept(new CloseReason(1000, "Normal closure"));
            } catch (Exception e) {
                System.err.println("Error in onClose handler: " + e.getMessage());
            }
        }

        // Call cleanup (release connection pool)
        if (onCleanup != null) {
            try {
                onCleanup.run();
            } catch (Exception e) {
                System.err.println("Error in cleanup handler: " + e.getMessage());
            }
        }
    }

    /**
     * Start reading messages (blocking)
     * This should be called in a separate thread
     */
    public void startReading() {
        try {
            while (!closed && !socket.isClosed()) {
                // Read frame header
                int firstByte = input.read();
                if (firstByte == -1)
                    break;

                boolean fin = (firstByte & 0x80) != 0;
                int opcode = firstByte & 0x0F;

                int secondByte = input.read();
                if (secondByte == -1)
                    break;

                boolean masked = (secondByte & 0x80) != 0;
                int payloadLength = secondByte & 0x7F;

                // Extended payload length
                if (payloadLength == 126) {
                    int byte1 = input.read();
                    int byte2 = input.read();
                    if (byte1 == -1 || byte2 == -1)
                        break;
                    payloadLength = (byte1 << 8) | byte2;
                } else if (payloadLength == 127) {
                    // Read 8 bytes for length (we'll limit to int max for simplicity)
                    long length = 0;
                    for (int i = 0; i < 8; i++) {
                        int b = input.read();
                        if (b == -1)
                            break;
                        length = (length << 8) | b;
                    }
                    if (length > Integer.MAX_VALUE) {
                        // Payload too large
                        break;
                    }
                    payloadLength = (int) length;
                }

                // Masking key
                byte[] maskingKey = null;
                if (masked) {
                    maskingKey = new byte[4];
                    int bytesRead = input.read(maskingKey);
                    if (bytesRead != 4)
                        break;
                }

                // Payload
                byte[] payload = new byte[payloadLength];
                int totalRead = 0;
                while (totalRead < payloadLength) {
                    int read = input.read(payload, totalRead, payloadLength - totalRead);
                    if (read == -1)
                        break;
                    totalRead += read;
                }

                if (totalRead != payloadLength)
                    break;

                // Unmask
                if (masked && maskingKey != null) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= maskingKey[i % 4];
                    }
                }

                // Handle frame based on opcode
                switch (opcode) {
                    case 0x1: // Text frame
                        if (fin && onMessage != null) {
                            String message = new String(payload, StandardCharsets.UTF_8);
                            try {
                                onMessage.accept(message);
                            } catch (Exception e) {
                                System.err.println("Error in onMessage handler: " + e.getMessage());
                            }
                        }
                        break;
                    case 0x8: // Close frame
                        close();
                        return;
                    case 0x9: // Ping frame
                        // Send pong
                        sendPong(payload);
                        break;
                    case 0xA: // Pong frame
                        // Ignore
                        break;
                    default:
                        // Unknown opcode, ignore or close
                        break;
                }
            }
        } catch (IOException e) {
            // Connection error
            if (!closed) {
                System.err.println("WebSocket read error: " + e.getMessage());
            }
        } finally {
            try {
                close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendPong(byte[] payload) {
        try {
            synchronized (output) {
                output.write(0x8A); // FIN=1, opcode=0xA (pong)
                output.write(payload.length);
                output.write(payload);
                output.flush();
            }
        } catch (IOException e) {
            // Failed to send pong, connection probably dead
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public record CloseReason(int code, String reason) {
    }
}