package com.nova.framework.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * WebSocket Frame Parser & Builder (RFC 6455)
 * Handles frame encoding/decoding for WebSocket protocol
 * 
 * @version 2.1.0 - Fixed integer overflow vulnerabilities
 */
public class WSFrame {
    
    // Opcodes (RFC 6455)
    public static final int OPCODE_CONTINUATION = 0x0;
    public static final int OPCODE_TEXT = 0x1;
    public static final int OPCODE_BINARY = 0x2;
    public static final int OPCODE_CLOSE = 0x8;
    public static final int OPCODE_PING = 0x9;
    public static final int OPCODE_PONG = 0xA;
    
    // Security limits
    private static final long MAX_PAYLOAD_SIZE = 10L * 1024 * 1024; // 10MB
    private static final int MAX_CONTROL_FRAME_SIZE = 125; // RFC 6455
    
    private final boolean fin;
    private final int opcode;
    private final boolean masked;
    private final byte[] payload;
    private final byte[] maskingKey;
    
    // Constructor
    private WSFrame(boolean fin, int opcode, boolean masked, byte[] payload, byte[] maskingKey) {
        this.fin = fin;
        this.opcode = opcode;
        this.masked = masked;
        this.payload = payload != null ? payload : new byte[0];
        this.maskingKey = maskingKey;
        
        // Validate
        validateFrame();
    }
    
    /**
     * Validate frame according to RFC 6455
     */
    private void validateFrame() {
        // Control frames must have FIN=1
        if (isControlFrame() && !fin) {
            throw new IllegalArgumentException("Control frames must have FIN bit set");
        }
        
        // Control frames payload must be <= 125 bytes
        if (isControlFrame() && payload.length > MAX_CONTROL_FRAME_SIZE) {
            throw new IllegalArgumentException(
                "Control frame payload too large: " + payload.length + " (max: " + MAX_CONTROL_FRAME_SIZE + ")"
            );
        }
        
        // Validate opcode
        if (opcode < 0 || opcode > 15) {
            throw new IllegalArgumentException("Invalid opcode: " + opcode);
        }
        
        // Reserved opcodes check
        if ((opcode >= 0x3 && opcode <= 0x7) || (opcode >= 0xB && opcode <= 0xF)) {
            throw new IllegalArgumentException("Reserved opcode: " + opcode);
        }
    }
    
    // ========== FACTORY METHODS ==========
    
    public static WSFrame text(String message) {
        if (message == null) {
            message = "";
        }
        byte[] payload = message.getBytes(StandardCharsets.UTF_8);
        return new WSFrame(true, OPCODE_TEXT, false, payload, null);
    }
    
    public static WSFrame binary(byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        return new WSFrame(true, OPCODE_BINARY, false, data, null);
    }
    
    public static WSFrame close() {
        return close(1000, "Normal closure");
    }
    
    public static WSFrame close(int statusCode, String reason) {
        // Validate status code (RFC 6455)
        if (!isValidCloseCode(statusCode)) {
            throw new IllegalArgumentException("Invalid close status code: " + statusCode);
        }
        
        if (reason == null) {
            reason = "";
        }
        
        byte[] reasonBytes = reason.getBytes(StandardCharsets.UTF_8);
        
        // Ensure total payload doesn't exceed 125 bytes
        if (reasonBytes.length > 123) {
            reasonBytes = truncateUtf8(reasonBytes, 123);
        }
        
        byte[] payload = new byte[2 + reasonBytes.length];
        payload[0] = (byte) ((statusCode >> 8) & 0xFF);
        payload[1] = (byte) (statusCode & 0xFF);
        System.arraycopy(reasonBytes, 0, payload, 2, reasonBytes.length);
        
        return new WSFrame(true, OPCODE_CLOSE, false, payload, null);
    }
    
    public static WSFrame ping() {
        return ping(new byte[0]);
    }
    
    public static WSFrame ping(byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        if (payload.length > MAX_CONTROL_FRAME_SIZE) {
            throw new IllegalArgumentException("Ping payload too large");
        }
        return new WSFrame(true, OPCODE_PING, false, payload, null);
    }
    
    public static WSFrame pong() {
        return pong(new byte[0]);
    }
    
    public static WSFrame pong(byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        if (payload.length > MAX_CONTROL_FRAME_SIZE) {
            throw new IllegalArgumentException("Pong payload too large");
        }
        return new WSFrame(true, OPCODE_PONG, false, payload, null);
    }
    
    // ========== READING FRAMES ==========
    
    /**
     * Read a WebSocket frame from input stream
     * 
     * @param in Input stream to read from
     * @return Parsed frame or null if stream closed
     * @throws IOException on read error or invalid frame
     */
    public static WSFrame read(InputStream in) throws IOException {
        if (in == null) {
            throw new IllegalArgumentException("Input stream cannot be null");
        }
        
        // Read first byte (FIN, RSV, Opcode)
        int b1 = in.read();
        if (b1 == -1) {
            return null; // Stream closed
        }
        
        boolean fin = (b1 & 0x80) != 0;
        boolean rsv1 = (b1 & 0x40) != 0;
        boolean rsv2 = (b1 & 0x20) != 0;
        boolean rsv3 = (b1 & 0x10) != 0;
        int opcode = b1 & 0x0F;
        
        // RSV bits must be 0 unless extension negotiated (RFC 6455)
        if (rsv1 || rsv2 || rsv3) {
            throw new IOException("RSV bits set without extension");
        }
        
        // Read second byte (Mask, Payload length)
        int b2 = in.read();
        if (b2 == -1) {
            throw new IOException("Unexpected end of stream reading mask/length");
        }
        
        boolean masked = (b2 & 0x80) != 0;
        long payloadLength = b2 & 0x7F;
        
        // Read extended payload length if needed
        payloadLength = readExtendedLength(in, payloadLength);
        
        // FIX: Enhanced validation to prevent integer overflow
        if (payloadLength < 0) {
            throw new IOException("Invalid payload length: " + payloadLength);
        }
        
        if (payloadLength > MAX_PAYLOAD_SIZE) {
            throw new IOException(
                "Payload too large: " + payloadLength + " bytes (max: " + MAX_PAYLOAD_SIZE + ")"
            );
        }
        
        // FIX: Check if payload length fits in int array
        if (payloadLength > Integer.MAX_VALUE) {
            throw new IOException("Payload length exceeds maximum array size: " + payloadLength);
        }
        
        // Read masking key (if masked)
        byte[] maskingKey = null;
        if (masked) {
            maskingKey = readBytes(in, 4);
        }
        
        // Read payload
        byte[] payload = readBytes(in, (int) payloadLength);
        
        // Unmask payload if needed
        if (masked && maskingKey != null) {
            unmaskPayload(payload, maskingKey);
        }
        
        return new WSFrame(fin, opcode, masked, payload, maskingKey);
    }
    
    /**
     * Read extended payload length with enhanced overflow protection
     * FIX: Validates length doesn't overflow or exceed limits
     */
    private static long readExtendedLength(InputStream in, long initialLength) throws IOException {
        if (initialLength == 126) {
            // 16-bit extended length
            int b1 = in.read();
            int b2 = in.read();
            if (b1 == -1 || b2 == -1) {
                throw new IOException("Unexpected end of stream reading 16-bit length");
            }
            long length = ((b1 & 0xFF) << 8) | (b2 & 0xFF);
            
            // Validate 16-bit length
            if (length < 126) {
                throw new IOException("Invalid 16-bit extended length (should use 7-bit): " + length);
            }
            
            return length;
            
        } else if (initialLength == 127) {
            // 64-bit extended length
            long length = 0;
            
            for (int i = 0; i < 8; i++) {
                int b = in.read();
                if (b == -1) {
                    throw new IOException("Unexpected end of stream reading 64-bit length");
                }
                
                // FIX: Check for overflow before shifting
                if (length > (Long.MAX_VALUE >> 8)) {
                    throw new IOException("Payload length overflow detected");
                }
                
                length = (length << 8) | (b & 0xFF);
            }
            
            // FIX: Additional validation
            // 1. Check if MSB is set (would indicate negative if cast to long)
            if (length < 0) {
                throw new IOException("Invalid payload length (MSB set): " + Long.toUnsignedString(length));
            }
            
            // 2. Validate 64-bit length requirement
            if (length <= 0xFFFF) {
                throw new IOException("Invalid 64-bit extended length (should use 16-bit): " + length);
            }
            
            // 3. Check against maximum allowed size
            if (length > MAX_PAYLOAD_SIZE) {
                throw new IOException("Payload length exceeds limit: " + length + " (max: " + MAX_PAYLOAD_SIZE + ")");
            }
            
            return length;
        }
        
        return initialLength;
    }
    
    /**
     * Read exact number of bytes from stream
     */
    private static byte[] readBytes(InputStream in, int count) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("Byte count cannot be negative");
        }
        
        if (count == 0) {
            return new byte[0];
        }
        
        byte[] buffer = new byte[count];
        int totalRead = 0;
        
        while (totalRead < count) {
            int read = in.read(buffer, totalRead, count - totalRead);
            if (read == -1) {
                throw new IOException(
                    String.format("Unexpected end of stream (read %d of %d bytes)", 
                                totalRead, count)
                );
            }
            totalRead += read;
        }
        
        return buffer;
    }
    
    /**
     * Unmask payload data using masking key (XOR operation)
     */
    private static void unmaskPayload(byte[] payload, byte[] maskingKey) {
        if (payload == null || maskingKey == null || maskingKey.length != 4) {
            return;
        }
        
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= maskingKey[i % 4];
        }
    }
    
    // ========== WRITING FRAMES ==========
    
    /**
     * Write this frame to output stream
     * 
     * @param out Output stream to write to
     * @throws IOException on write error
     */
    public void write(OutputStream out) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("Output stream cannot be null");
        }
        
        // Write first byte (FIN + opcode)
        int b1 = (fin ? 0x80 : 0x00) | (opcode & 0x0F);
        out.write(b1);
        
        // Write second byte (mask flag + payload length)
        writePayloadLength(out, payload.length, masked);
        
        // Write masking key if masked
        if (masked && maskingKey != null) {
            out.write(maskingKey);
        }
        
        // Write payload (masked if needed)
        if (payload.length > 0) {
            if (masked && maskingKey != null) {
                byte[] maskedPayload = new byte[payload.length];
                System.arraycopy(payload, 0, maskedPayload, 0, payload.length);
                unmaskPayload(maskedPayload, maskingKey); // XOR is reversible
                out.write(maskedPayload);
            } else {
                out.write(payload);
            }
        }
        
        out.flush();
    }
    
    /**
     * Write payload length with proper encoding
     */
    private void writePayloadLength(OutputStream out, int length, boolean masked) throws IOException {
        if (length < 126) {
            // Short length (7 bits)
            int b2 = (masked ? 0x80 : 0x00) | length;
            out.write(b2);
            
        } else if (length <= 65535) {
            // Medium length (16 bits)
            int b2 = (masked ? 0x80 : 0x00) | 126;
            out.write(b2);
            out.write((length >> 8) & 0xFF);
            out.write(length & 0xFF);
            
        } else {
            // Long length (64 bits)
            int b2 = (masked ? 0x80 : 0x00) | 127;
            out.write(b2);
            
            // Write 64-bit length (high 32 bits are zero for int)
            for (int i = 7; i >= 0; i--) {
                if (i >= 4) {
                    out.write(0); // High 32 bits
                } else {
                    out.write((length >> (i * 8)) & 0xFF);
                }
            }
        }
    }
    
    // ========== GETTERS ==========
    
    public boolean isFin() { return fin; }
    public int getOpcode() { return opcode; }
    public boolean isMasked() { return masked; }
    public byte[] getPayload() { return payload.clone(); } // Return copy for safety
    public byte[] getMaskingKey() { return maskingKey != null ? maskingKey.clone() : null; }
    
    // Frame type checks
    public boolean isText() { return opcode == OPCODE_TEXT; }
    public boolean isBinary() { return opcode == OPCODE_BINARY; }
    public boolean isClose() { return opcode == OPCODE_CLOSE; }
    public boolean isPing() { return opcode == OPCODE_PING; }
    public boolean isPong() { return opcode == OPCODE_PONG; }
    public boolean isContinuation() { return opcode == OPCODE_CONTINUATION; }
    
    public boolean isControlFrame() {
        return opcode >= 0x8; // Close, Ping, Pong
    }
    
    public boolean isDataFrame() {
        return opcode < 0x8; // Text, Binary, Continuation
    }
    
    // Payload as text
    public String getTextPayload() {
        return new String(payload, StandardCharsets.UTF_8);
    }
    
    // Close frame info
    public int getCloseStatusCode() {
        if (!isClose() || payload.length < 2) {
            return -1;
        }
        return ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF);
    }
    
    public String getCloseReason() {
        if (!isClose() || payload.length <= 2) {
            return "";
        }
        return new String(payload, 2, payload.length - 2, StandardCharsets.UTF_8);
    }
    
    // ========== UTILITIES ==========
    
    /**
     * Validate close status code (RFC 6455 Section 7.4.1)
     */
    private static boolean isValidCloseCode(int code) {
        // Valid ranges
        if (code >= 1000 && code <= 1003) return true;
        if (code >= 1007 && code <= 1011) return true;
        if (code >= 3000 && code <= 4999) return true;
        
        // Reserved codes
        return false;
    }
    
    /**
     * Truncate UTF-8 bytes to valid UTF-8 at max length
     */
    private static byte[] truncateUtf8(byte[] bytes, int maxLength) {
        if (bytes.length <= maxLength) {
            return bytes;
        }
        
        // Find valid UTF-8 boundary
        int len = maxLength;
        while (len > 0 && (bytes[len] & 0xC0) == 0x80) {
            len--; // Skip continuation bytes
        }
        
        byte[] result = new byte[len];
        System.arraycopy(bytes, 0, result, 0, len);
        return result;
    }
    
    @Override
    public String toString() {
        String type = switch (opcode) {
            case OPCODE_CONTINUATION -> "CONTINUATION";
            case OPCODE_TEXT -> "TEXT";
            case OPCODE_BINARY -> "BINARY";
            case OPCODE_CLOSE -> "CLOSE";
            case OPCODE_PING -> "PING";
            case OPCODE_PONG -> "PONG";
            default -> "UNKNOWN(" + opcode + ")";
        };
        
        return String.format(
            "WSFrame{type=%s, fin=%s, masked=%s, length=%d}",
            type, fin, masked, payload.length
        );
    }
}