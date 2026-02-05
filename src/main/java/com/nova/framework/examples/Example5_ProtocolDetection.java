package com.nova.framework.examples;

import com.nova.framework.NovaServer;
import com.nova.framework.plugins.ProtocolDetectionPlugin;

import java.io.*;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Example 5: Custom Protocol Detection
 * 
 * Demonstrates:
 * - Protocol detection via magic bytes
 * - Multiple custom protocol handlers
 * - Binary protocol support
 * - Default/fallback handler
 * - Mixing HTTP and custom protocols on same port
 */
public class Example5_ProtocolDetection {

    public static void main(String[] args) {
        try {
            NovaServer server = new NovaServer(8080);

            // Register Protocol Detection Plugin
            ProtocolDetectionPlugin protocol = new ProtocolDetectionPlugin();
            server.use(protocol);

            // ========== STANDARD HTTP ROUTES ==========
            
            server.routing().get("/", (req, res) -> {
                res.html("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Protocol Detection Demo</title>
                        <style>
                            body { font-family: Arial; max-width: 900px; margin: 50px auto; padding: 20px; }
                            .protocol { background: #f4f4f4; padding: 15px; margin: 15px 0; border-radius: 5px; border-left: 4px solid #2196F3; }
                            code { background: #e0e0e0; padding: 2px 5px; border-radius: 3px; }
                            pre { background: #2d2d2d; color: #f8f8f2; padding: 15px; border-radius: 5px; overflow-x: auto; }
                            .magic { color: #66d9ef; font-weight: bold; }
                        </style>
                    </head>
                    <body>
                        <h1>🔌 Custom Protocol Detection</h1>
                        <p>This server supports HTTP and multiple custom binary protocols on the same port!</p>
                        
                        <h2>Supported Protocols:</h2>
                        
                        <div class="protocol">
                            <h3>1. HTTP (Standard Web)</h3>
                            <p>Any request starting with HTTP methods (GET, POST, etc.)</p>
                            <pre><code>curl http://localhost:8080/
curl http://localhost:8080/api/info</code></pre>
                        </div>
                        
                        <div class="protocol">
                            <h3>2. NOVA Protocol</h3>
                            <p>Magic bytes: <span class="magic">4E 4F 56 41</span> (ASCII: "NOVA")</p>
                            <p>Simple request-response protocol</p>
                            <pre><code># Send NOVA protocol request
echo -n "NOVAHello from NOVA protocol!" | nc localhost 8080</code></pre>
                        </div>
                        
                        <div class="protocol">
                            <h3>3. ECHO Protocol</h3>
                            <p>Magic bytes: <span class="magic">45 43 48 4F</span> (ASCII: "ECHO")</p>
                            <p>Echoes back everything you send</p>
                            <pre><code># Send ECHO protocol request
echo -n "ECHOTest message 123" | nc localhost 8080</code></pre>
                        </div>
                        
                        <div class="protocol">
                            <h3>4. BINARY Protocol</h3>
                            <p>Magic bytes: <span class="magic">FF FE FD FC</span></p>
                            <p>Binary data protocol with length prefix</p>
                            <pre><code># Send binary protocol (hex escapes)
printf "\\xff\\xfe\\xfd\\xfc\\x00\\x05Hello" | nc localhost 8080</code></pre>
                        </div>
                        
                        <div class="protocol">
                            <h3>5. Unknown Protocols</h3>
                            <p>Any other magic bytes → Default handler</p>
                            <pre><code># Send unknown protocol
echo -n "TEST123" | nc localhost 8080</code></pre>
                        </div>
                        
                        <h2>Python Test Client:</h2>
                        <pre><code>python3 test_protocols.py</code></pre>
                    </body>
                    </html>
                    """);
            });

            server.routing().get("/api/info", (req, res) -> {
                res.json("""
                    {
                        "server": "NovaServer v3.0",
                        "protocols": ["HTTP", "NOVA", "ECHO", "BINARY"],
                        "port": 8080,
                        "status": "running"
                    }
                    """);
            });

            // ========== CUSTOM PROTOCOL HANDLERS ==========

            // 1. NOVA Protocol Handler (Magic: "NOVA" = 0x4E4F5641)
            protocol.onCustomProtocol(new byte[]{0x4E, 0x4F, 0x56, 0x41}, (socket, input, magic) -> {
                System.out.println("📡 NOVA protocol detected!");
                
                try {
                    OutputStream output = socket.getOutputStream();
                    
                    // Read remaining message (after magic bytes which are already in 'magic' param)
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] temp = new byte[1024];
                    socket.setSoTimeout(1000); // 1 second timeout
                    
                    try {
                        int bytesRead;
                        while ((bytesRead = input.read(temp)) != -1) {
                            buffer.write(temp, 0, bytesRead);
                            if (input.available() == 0) break;
                        }
                    } catch (Exception e) {
                        // Timeout or end of stream
                    }
                    
                    String message = buffer.toString(StandardCharsets.UTF_8);
                    System.out.println("NOVA Message: " + message);
                    
                    // Send response
                    String response = "NOVA_RESPONSE: Message received (" + message.length() + " bytes)\n";
                    output.write(response.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    
                } catch (IOException e) {
                    System.err.println("NOVA protocol error: " + e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            // 2. ECHO Protocol Handler (Magic: "ECHO" = 0x4543484F)
            protocol.onCustomProtocol(new byte[]{0x45, 0x43, 0x48, 0x4F}, (socket, input, magic) -> {
                System.out.println("🔊 ECHO protocol detected!");
                
                try {
                    OutputStream output = socket.getOutputStream();
                    
                    // Read all data
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] temp = new byte[1024];
                    socket.setSoTimeout(1000);
                    
                    try {
                        int bytesRead;
                        while ((bytesRead = input.read(temp)) != -1) {
                            buffer.write(temp, 0, bytesRead);
                            if (input.available() == 0) break;
                        }
                    } catch (Exception e) {
                        // Timeout
                    }
                    
                    byte[] data = buffer.toByteArray();
                    System.out.println("ECHO Data: " + new String(data, StandardCharsets.UTF_8) + 
                                       " (" + data.length + " bytes)");
                    
                    // Echo back with prefix
                    output.write("ECHO: ".getBytes(StandardCharsets.UTF_8));
                    output.write(data);
                    output.write("\n".getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    
                } catch (IOException e) {
                    System.err.println("ECHO protocol error: " + e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            // 3. Binary Protocol Handler (Magic: 0xFFFEFDFC)
            protocol.onCustomProtocol(
                new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC},
                (socket, input, magic) -> {

                    System.out.println("BINARY protocol detected");

                    try {
                        socket.setSoTimeout(5000); // timeout

                        DataInputStream din = new DataInputStream(input);
                        
                        // MAGIC'i atla
                        byte[] magicBytes = new byte[4];
                        din.readFully(magicBytes);

                        OutputStream out = socket.getOutputStream();

                        // length (2 byte)
                        int length = din.readUnsignedShort();
                        System.out.println("Expected length: " + length);

                        // data
                        byte[] data = new byte[length];
                        din.readFully(data); // bloklanmaz (timeout var)

                        System.out.println("Received: " + data.length + " bytes");
                        System.out.println("Data: " + new String(data, StandardCharsets.UTF_8));

                        // response
                        out.write(new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD, (byte) 0xFC});

                        int responseLength = 3 + data.length; // "OK\n" + data
                        out.write((responseLength >> 8) & 0xFF);
                        out.write(responseLength & 0xFF);

                        out.write("OK\n".getBytes(StandardCharsets.UTF_8));
                        out.write(data);
                        out.flush();

                    } catch (SocketTimeoutException e) {
                        System.err.println("Timeout");
                    } catch (IOException e) {
                        System.err.println("BINARY error: " + e.getMessage());
                    } finally {
                        try {
                            socket.close();
                        } catch (IOException ignored) {}
                    }
                }
            );


            // 4. Default Handler (for unknown protocols)
            protocol.onCustomProtocol((socket, input, magic) -> {
                System.out.println("❓ Unknown protocol detected: " + bytesToHex(magic));
                
                try {
                    OutputStream output = socket.getOutputStream();
                    
                    String response = """
                        UNKNOWN PROTOCOL
                        
                        This server supports:
                        - HTTP (GET, POST, etc.)
                        - NOVA protocol (magic: 4E4F5641)
                        - ECHO protocol (magic: 4543484F)
                        - BINARY protocol (magic: FFFEFDFC)
                        
                        Your protocol was not recognized.
                        Magic bytes: %s
                        """.formatted(bytesToHex(magic));
                    
                    output.write(response.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                    
                } catch (IOException e) {
                    System.err.println("Default handler error: " + e.getMessage());
                } finally {
                    try {
                        socket.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            // Start server
            server.start();

            System.out.println("╔════════════════════════════════════════════════════╗");
            System.out.println("║   NovaServer - Protocol Detection Demo            ║");
            System.out.println("╠════════════════════════════════════════════════════╣");
            System.out.println("║  Port: 8080                                        ║");
            System.out.println("║  Protocols: HTTP, NOVA, ECHO, BINARY               ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println();
            System.out.println("🌐 HTTP:");
            System.out.println("   • http://localhost:8080/");
            System.out.println("   • http://localhost:8080/api/info");
            System.out.println();
            System.out.println("🔌 Custom Protocols (using netcat):");
            System.out.println("   • echo -n 'NOVAHello World!' | nc localhost 8080");
            System.out.println("   • echo -n 'ECHOTest message' | nc localhost 8080");
            System.out.println("   • echo -n 'UNKNOWN' | nc localhost 8080");
            System.out.println();
            System.out.println("⚙️ Binary Protocol:");
            System.out.println("   • printf '\\xff\\xfe\\xfd\\xfc\\x00\\x05Hello' | nc localhost 8080");
            System.out.println();
            System.out.println("🐍 Python Test Client:");
            System.out.println("   • python3 test_protocols.py");
            System.out.println();
            System.out.println("Press Ctrl+C to stop...");

            server.await();

        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return hex.toString();
    }
}
