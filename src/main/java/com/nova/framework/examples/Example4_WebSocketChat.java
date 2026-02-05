package com.nova.framework.examples;

import com.nova.framework.NovaServer;
import com.nova.framework.plugins.WebSocketPlugin;
import com.nova.framework.plugins.websocket.WebSocketConnection;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example 4: WebSocket Chat Server
 * 
 * Demonstrates:
 * - WebSocket plugin usage
 * - Real-time bidirectional communication
 * - Broadcasting to multiple clients
 * - Connection management
 * - Chat room functionality
 */
public class Example4_WebSocketChat {

    // Store all connected clients
    private static final Map<String, WebSocketConnection> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        try {
            NovaServer server = new NovaServer(8080);

            // Register WebSocket plugin
            WebSocketPlugin ws = new WebSocketPlugin();
            server.use(ws);

            // WebSocket chat endpoint
            ws.websocket("/chat", client -> {
                // Store the connection
                clients.put(client.getId(), client);
                System.out.println("✅ Client connected: " + client.getId() + 
                                   " (Total: " + clients.size() + ")");

                // Broadcast join message to all clients
                String joinMsg = createMessage("System", 
                    client.getId() + " joined the chat", "system");
                broadcast(joinMsg, null);

                // Handle incoming messages
                client.onMessage(message -> {
                    System.out.println("📨 Message from " + client.getId() + ": " + message);

                    // Create formatted message
                    String chatMsg = createMessage(client.getId(), message, "user");

                    // Broadcast to all clients except sender
                    broadcast(chatMsg, client.getId());
                });

                // Handle client disconnect
                client.onClose(reason -> {
                    clients.remove(client.getId());
                    System.out.println("❌ Client disconnected: " + client.getId() + 
                                       " (Total: " + clients.size() + ")");

                    // Broadcast leave message
                    String leaveMsg = createMessage("System", 
                        client.getId() + " left the chat", "system");
                    broadcast(leaveMsg, null);
                });

                // Send welcome message to the new client
                try {
                    String welcome = createMessage("System", 
                        "Welcome to the chat! You are " + client.getId(), "system");
                    client.send(welcome);

                    // Send current user count
                    client.send(createMessage("System", 
                        "Currently " + clients.size() + " user(s) online", "system"));
                } catch (IOException e) {
                    System.err.println("Failed to send welcome message: " + e.getMessage());
                }
            });

            // HTTP endpoint - chat client page
            server.routing().get("/", (req, res) -> {
                res.html("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>WebSocket Chat</title>
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            body { font-family: Arial, sans-serif; background: #f0f0f0; }
                            .container { max-width: 800px; margin: 0 auto; padding: 20px; }
                            h1 { text-align: center; color: #333; margin-bottom: 20px; }
                            .chat-box { 
                                background: white; 
                                border-radius: 8px; 
                                box-shadow: 0 2px 10px rgba(0,0,0,0.1); 
                                height: 500px;
                                display: flex;
                                flex-direction: column;
                            }
                            .messages { 
                                flex: 1; 
                                overflow-y: auto; 
                                padding: 20px; 
                                border-bottom: 1px solid #ddd;
                            }
                            .message { 
                                margin: 10px 0; 
                                padding: 10px; 
                                border-radius: 5px;
                                max-width: 70%;
                            }
                            .message.user { 
                                background: #e3f2fd; 
                                margin-left: auto;
                            }
                            .message.other { 
                                background: #f5f5f5; 
                            }
                            .message.system { 
                                background: #fff3cd; 
                                text-align: center;
                                margin: 10px auto;
                                max-width: 90%;
                                font-style: italic;
                            }
                            .message .sender { 
                                font-weight: bold; 
                                margin-bottom: 5px;
                                color: #1976d2;
                            }
                            .message.system .sender { color: #856404; }
                            .input-area { 
                                display: flex; 
                                padding: 20px;
                                gap: 10px;
                            }
                            input { 
                                flex: 1; 
                                padding: 12px; 
                                border: 1px solid #ddd; 
                                border-radius: 5px;
                                font-size: 14px;
                            }
                            button { 
                                padding: 12px 24px; 
                                background: #1976d2; 
                                color: white; 
                                border: none; 
                                border-radius: 5px; 
                                cursor: pointer;
                                font-size: 14px;
                                font-weight: bold;
                            }
                            button:hover { background: #1565c0; }
                            button:disabled { background: #ccc; cursor: not-allowed; }
                            .status { 
                                text-align: center; 
                                padding: 10px; 
                                background: #4caf50; 
                                color: white;
                                border-radius: 5px;
                                margin-bottom: 20px;
                            }
                            .status.disconnected { background: #f44336; }
                        </style>
                    </head>
                    <body>
                        <div class="container">
                            <h1>💬 WebSocket Chat Room</h1>
                            <div class="status" id="status">Connecting...</div>
                            <div class="chat-box">
                                <div class="messages" id="messages"></div>
                                <div class="input-area">
                                    <input type="text" id="messageInput" placeholder="Type a message..." disabled>
                                    <button id="sendBtn" disabled>Send</button>
                                </div>
                            </div>
                        </div>
                        
                        <script>
                            let ws;
                            const messagesDiv = document.getElementById('messages');
                            const messageInput = document.getElementById('messageInput');
                            const sendBtn = document.getElementById('sendBtn');
                            const statusDiv = document.getElementById('status');
                            
                            function connect() {
                                ws = new WebSocket('ws://localhost:8080/chat');
                                
                                ws.onopen = () => {
                                    console.log('Connected to chat server');
                                    statusDiv.textContent = '✅ Connected';
                                    statusDiv.className = 'status';
                                    messageInput.disabled = false;
                                    sendBtn.disabled = false;
                                    messageInput.focus();
                                };
                                
                                ws.onmessage = (event) => {
                                    const data = JSON.parse(event.data);
                                    addMessage(data.sender, data.message, data.type);
                                };
                                
                                ws.onclose = () => {
                                    console.log('Disconnected from chat server');
                                    statusDiv.textContent = '❌ Disconnected - Reconnecting...';
                                    statusDiv.className = 'status disconnected';
                                    messageInput.disabled = true;
                                    sendBtn.disabled = true;
                                    setTimeout(connect, 2000);
                                };
                                
                                ws.onerror = (error) => {
                                    console.error('WebSocket error:', error);
                                };
                            }
                            
                            function addMessage(sender, message, type) {
                                const msgDiv = document.createElement('div');
                                msgDiv.className = 'message ' + type;
                                msgDiv.innerHTML = '<div class="sender">' + sender + '</div>' + message;
                                messagesDiv.appendChild(msgDiv);
                                messagesDiv.scrollTop = messagesDiv.scrollHeight;
                            }
                            
                            function sendMessage() {
                                const message = messageInput.value.trim();
                                if (message && ws.readyState === WebSocket.OPEN) {
                                    ws.send(message);
                                    messageInput.value = '';
                                }
                            }
                            
                            sendBtn.onclick = sendMessage;
                            messageInput.onkeypress = (e) => {
                                if (e.key === 'Enter') sendMessage();
                            };
                            
                            connect();
                        </script>
                    </body>
                    </html>
                    """);
            });

            // API endpoint - get chat stats
            server.routing().get("/api/stats", (req, res) -> {
                res.json("""
                    {
                        "connected_users": %d,
                        "active_connections": %d,
                        "server": "NovaServer v3.0"
                    }
                    """.formatted(clients.size(), clients.size()));
            });

            server.start();

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   NovaServer - WebSocket Chat          ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  Web: http://localhost:8080/           ║");
            System.out.println("║  WS:  ws://localhost:8080/chat         ║");
            System.out.println("║  API: http://localhost:8080/api/stats  ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println("\n💡 Open http://localhost:8080/ in multiple browser tabs");
            System.out.println("   to test real-time chat functionality!\n");

            server.await();

        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Broadcast message to all clients (except excluded one)
     */
    private static void broadcast(String message, String excludeId) {
        clients.forEach((id, client) -> {
            if (!id.equals(excludeId)) {
                try {
                    client.send(message);
                } catch (IOException e) {
                    System.err.println("Failed to send to " + id + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Create formatted chat message (JSON)
     */
    private static String createMessage(String sender, String message, String type) {
        // Escape quotes in message
        message = message.replace("\"", "\\\"");
        return String.format(
            "{\"sender\":\"%s\",\"message\":\"%s\",\"type\":\"%s\",\"timestamp\":%d}",
            sender, message, type, System.currentTimeMillis()
        );
    }
}
