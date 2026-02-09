# 🚀 NovaServer v3.5

**Modern, Plugin-Based Java HTTP Server Framework**

NovaServer v3.5 is a lightweight, extensible HTTP server framework built with a plugin-based architecture. It supports HTTP/1.1, WebSockets, custom protocols, middleware, routing, and more - all while maintaining a minimal core.

[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Version](https://img.shields.io/badge/version-3.0.0-green.svg)](https://github.com/yourusername/novaserver)

---

## ✨ Features

### Core Features
- **🔌 Plugin-Based Architecture** - Modular design with dependency resolution
- **⚡ Virtual Threads Support** - Java 21+ Project Loom integration
- **🌐 HTTP/1.1 Server** - Full HTTP protocol support
- **🔄 WebSocket Support** - Real-time bidirectional communication
- **🛣️ Advanced Routing** - Path parameters, query parameters, RESTful APIs
- **🔗 Middleware System** - Request/response pipeline
- **📡 Protocol Detection** - Custom binary protocol support via magic bytes
- **🔒 SSL/TLS Support** - HTTPS with configurable keystore
- **📊 Connection Pool** - Thread-safe connection management
- **🍪 Cookie Management** - Full cookie support with security features

### Built-in Plugins
1. **RoutingPlugin** (Default) - HTTP routing with path parameters
2. **MiddlewarePlugin** (Default) - Request/response middleware chain
3. **WebSocketPlugin** (Optional) - WebSocket protocol implementation
4. **SSLPlugin** (Optional) - SSL/TLS encryption support
5. **ProtocolDetectionPlugin** (Optional) - Custom protocol detection

---

## 📦 Quick Start

### Basic HTTP Server

```java
import com.nova.framework.NovaServer;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        NovaServer server = new NovaServer(8080);
        
        server.routing().get("/", (req, res) -> {
            res.send("Hello, NovaServer v3.0! 👋");
        });
        
        server.start();
        System.out.println("Server running at http://localhost:8080");
        server.await();
    }
}
```

### RESTful API Example

```java
NovaServer server = new NovaServer(8080);

// GET /api/users
server.routing().get("/api/users", (req, res) -> {
    res.json("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]");
});

// GET /api/users/:id
server.routing().get("/api/users/:id", (req, res) -> {
    String id = req.getPathParam("id");
    res.json("{\"id\":" + id + ",\"name\":\"User " + id + "\"}");
});

// POST /api/users
server.routing().post("/api/users", (req, res) -> {
    String body = req.body();
    res.status(201).json("{\"message\":\"User created\"}");
});

server.start();
```

---

## 🔧 Configuration

### Using Builder Pattern

```java
NovaConfig config = NovaConfig.builder()
    .port(8080)
    .maxConnections(10000)              // Max concurrent connections
    .maxRequestSize(10 * 1024 * 1024)   // 10MB max request
    .socketTimeout(30000)               // 30s socket timeout
    .shutdownTimeout(10)                // 10s graceful shutdown
    .workerThreads(0)                   // Auto (CPU cores * 2)
    .useVirtualThreads(true)            // Java 21+ virtual threads
    .build();

NovaServer server = new NovaServer(config);
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `port` | 8080 | Server port (1-65535) |
| `maxConnections` | 10000 | Maximum concurrent connections |
| `maxRequestSize` | 10 MB | Maximum request body size |
| `socketTimeout` | 30000 ms | Socket read timeout |
| `shutdownTimeout` | 10 s | Graceful shutdown timeout |
| `workerThreads` | Auto | Thread pool size (0 = auto) |
| `useVirtualThreads` | true | Use virtual threads if available |

---

## 🛣️ Routing

### Path Parameters

```java
// Dynamic routes with parameters
server.routing().get("/users/:id", (req, res) -> {
    String userId = req.getPathParam("id");
    res.json("{\"id\":\"" + userId + "\"}");
});

server.routing().get("/posts/:postId/comments/:commentId", (req, res) -> {
    String postId = req.getPathParam("postId");
    String commentId = req.getPathParam("commentId");
    res.json("{\"post\":\"" + postId + "\",\"comment\":\"" + commentId + "\"}");
});
```

### Query Parameters

```java
// GET /search?q=java&limit=10
server.routing().get("/search", (req, res) -> {
    String query = req.getQueryParam("q");
    String limit = req.getQueryParam("limit");
    res.json("{\"query\":\"" + query + "\",\"limit\":\"" + limit + "\"}");
});
```

### HTTP Methods

```java
server.routing().get("/resource", handler);      // GET
server.routing().post("/resource", handler);     // POST
server.routing().put("/resource/:id", handler);  // PUT
server.routing().delete("/resource/:id", handler); // DELETE
```

### Request Object

```java
server.routing().get("/info", (req, res) -> {
    // Path and parameters
    String path = req.path();
    String id = req.getPathParam("id");
    String query = req.getQueryParam("search");
    
    // Headers
    String contentType = req.getHeader("Content-Type");
    String userAgent = req.getHeader("User-Agent");
    
    // Body
    String body = req.body();
    
    // Cookies
    String sessionId = req.getCookie("session");
    
    // Client info
    String clientIp = req.clientAddress();
    
    // Method
    HTTPMethod method = req.method();
});
```

### Response Object

```java
server.routing().get("/demo", (req, res) -> {
    // Plain text
    res.send("Hello World");
    
    // HTML
    res.html("<h1>Hello</h1>");
    
    // JSON
    res.json("{\"status\":\"ok\"}");
    
    // Custom content type
    res.send("data", "application/xml");
    
    // Status code
    res.status(201).json("{\"created\":true}");
    
    // Headers
    res.setHeader("X-Custom", "value")
       .contentType("application/json")
       .send("data");
    
    // Cookies
    res.cookie("session", "abc123");
    res.cookie(Cookie.builder("user", "john")
        .path("/")
        .maxAge(3600)
        .secure(true)
        .httpOnly(true)
        .sameSite("Strict")
        .build());
    
    // File download
    res.sendFile(new File("report.pdf"));
    res.sendFile(new File("data.csv"), "custom-name.csv");
});
```

---

## 🔗 Middleware

Middleware functions execute in order before route handlers.

### Basic Middleware

```java
import com.nova.framework.plugins.MiddlewarePlugin.MiddlewareResult;

server.middleware().use((req, res) -> {
    System.out.println(req.method() + " " + req.path());
    return MiddlewareResult.CONTINUE;
});
```

### Authentication Middleware

```java
server.middleware().use((req, res) -> {
    // Skip auth for public routes
    if (req.path().startsWith("/public")) {
        return MiddlewareResult.CONTINUE;
    }
    
    // Check authorization header
    String auth = req.getHeader("Authorization");
    if (auth == null || !auth.startsWith("Bearer ")) {
        res.status(401).json("{\"error\":\"Unauthorized\"}");
        return MiddlewareResult.STOP; // Stop processing
    }
    
    String token = auth.substring(7);
    if (!isValidToken(token)) {
        res.status(403).json("{\"error\":\"Forbidden\"}");
        return MiddlewareResult.STOP;
    }
    
    return MiddlewareResult.CONTINUE; // Continue to next middleware/route
});
```

### CORS Middleware

```java
server.middleware().use((req, res) -> {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
    
    // Handle preflight
    if (req.method().toString().equals("OPTIONS")) {
        res.status(204).send("");
        return MiddlewareResult.STOP;
    }
    
    return MiddlewareResult.CONTINUE;
});
```

### Logging Middleware

```java
server.middleware().use((req, res) -> {
    long start = System.currentTimeMillis();
    String method = req.method().toString();
    String path = req.path();
    String ip = req.clientAddress();
    
    System.out.printf("[%s] %s %s from %s%n", 
        LocalDateTime.now(), method, path, ip);
    
    // Add response time header
    long duration = System.currentTimeMillis() - start;
    res.setHeader("X-Response-Time", duration + "ms");
    
    return MiddlewareResult.CONTINUE;
});
```

---

## 🔄 WebSocket Support

### Basic WebSocket Server

```java
import com.nova.framework.plugins.WebSocketPlugin;

NovaServer server = new NovaServer(8080);

// Register WebSocket plugin
WebSocketPlugin ws = new WebSocketPlugin();
server.use(ws);

// WebSocket endpoint
ws.websocket("/chat", client -> {
    System.out.println("Client connected: " + client.getId());
    
    // Handle messages
    client.onMessage(message -> {
        System.out.println("Received: " + message);
        client.send("Echo: " + message);
    });
    
    // Handle close
    client.onClose(reason -> {
        System.out.println("Client disconnected: " + reason.code());
    });
    
    // Send welcome message
    client.send("Welcome to WebSocket server!");
});

server.start();
```

### WebSocket Chat Room

```java
Map<String, WebSocketConnection> clients = new ConcurrentHashMap<>();

ws.websocket("/chat", client -> {
    // Store connection
    clients.put(client.getId(), client);
    
    // Broadcast join message
    broadcast("User " + client.getId() + " joined", client.getId());
    
    // Handle messages
    client.onMessage(message -> {
        // Broadcast to all except sender
        broadcast(client.getId() + ": " + message, client.getId());
    });
    
    // Handle disconnect
    client.onClose(reason -> {
        clients.remove(client.getId());
        broadcast("User " + client.getId() + " left", null);
    });
});

void broadcast(String message, String excludeId) {
    clients.forEach((id, client) -> {
        if (!id.equals(excludeId)) {
            try {
                client.send(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    });
}
```

### WebSocket Client (JavaScript)

```javascript
const ws = new WebSocket('ws://localhost:8080/chat');

ws.onopen = () => {
    console.log('Connected');
    ws.send('Hello from browser!');
};

ws.onmessage = (event) => {
    console.log('Received:', event.data);
};

ws.onclose = () => {
    console.log('Disconnected');
};
```

---

## 📡 Custom Protocol Detection

NovaServer can detect and route custom binary protocols on the same port as HTTP.

### Register Custom Protocol

```java
import com.nova.framework.plugins.ProtocolDetectionPlugin;

NovaServer server = new NovaServer(8080);

// Register protocol detection plugin
ProtocolDetectionPlugin protocol = new ProtocolDetectionPlugin();
server.use(protocol);

// Custom protocol with magic bytes "NOVA" (0x4E4F5641)
protocol.onCustomProtocol(
    new byte[]{0x4E, 0x4F, 0x56, 0x41},
    (socket, input, magic) -> {
        System.out.println("NOVA protocol detected!");
        
        OutputStream output = socket.getOutputStream();
        
        // Read message
        byte[] buffer = new byte[1024];
        int bytesRead = input.read(buffer);
        String message = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
        
        // Send response
        String response = "NOVA_RESPONSE: " + message + "\n";
        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.flush();
        
        socket.close();
    }
);

server.start();
```

### Binary Protocol Example

```java
// Binary protocol with magic bytes 0xFFFEFDFC
protocol.onCustomProtocol(
    new byte[]{(byte)0xFF, (byte)0xFE, (byte)0xFD, (byte)0xFC},
    (socket, input, magic) -> {
        DataInputStream din = new DataInputStream(input);
        OutputStream out = socket.getOutputStream();
        
        // Read length-prefixed message
        int length = din.readUnsignedShort();
        byte[] data = new byte[length];
        din.readFully(data);
        
        System.out.println("Received " + length + " bytes");
        
        // Send response
        byte[] response = "OK".getBytes(StandardCharsets.UTF_8);
        out.write(new byte[]{(byte)0xFF, (byte)0xFE, (byte)0xFD, (byte)0xFC});
        out.write((response.length >> 8) & 0xFF);
        out.write(response.length & 0xFF);
        out.write(response);
        out.flush();
        
        socket.close();
    }
);
```

### Default Handler (Unknown Protocols)

```java
// Fallback for unrecognized protocols
protocol.onCustomProtocol((socket, input, magic) -> {
    System.out.println("Unknown protocol: " + bytesToHex(magic));
    
    OutputStream output = socket.getOutputStream();
    output.write("ERROR: Unknown protocol\n".getBytes());
    output.flush();
    socket.close();
});
```

### Testing Custom Protocols

```bash
# NOVA protocol
echo -n "NOVAHello World!" | nc localhost 8080

# ECHO protocol
echo -n "ECHOTest message" | nc localhost 8080

# Binary protocol
printf "\xff\xfe\xfd\xfc\x00\x05Hello" | nc localhost 8080

# Unknown protocol
echo -n "TEST123" | nc localhost 8080
```

---

## 🔒 SSL/TLS Support

### Configure HTTPS

```java
import com.nova.framework.plugins.SSLPlugin;

NovaServer server = new NovaServer(8443);

// Register and configure SSL plugin
SSLPlugin ssl = new SSLPlugin();
ssl.configure("keystore.jks", "password123");
server.use(ssl);

// Your routes...
server.routing().get("/", (req, res) -> {
    res.send("Secure HTTPS connection!");
});

server.start();
```

### Generate Self-Signed Certificate

```bash
keytool -genkeypair -keyalg RSA -keysize 2048 \
    -keystore keystore.jks -storepass password123 \
    -alias novaserver -dname "CN=localhost" \
    -validity 365
```

---

## 🔌 Plugin System

### Create Custom Plugin

```java
import com.nova.framework.plugin.*;

public class CustomPlugin extends BasePlugin {
    
    @Override
    public String id() {
        return "custom-plugin";
    }
    
    @Override
    public String name() {
        return "My Custom Plugin";
    }
    
    @Override
    public String version() {
        return "1.0.0";
    }
    
    @Override
    public boolean isDefault() {
        return false; // Optional plugin
    }
    
    @Override
    public PluginPriority priority() {
        return PluginPriority.NORMAL;
    }
    
    @Override
    public Set<String> dependencies() {
        return Set.of("routing"); // Depends on routing plugin
    }
    
    @Override
    public void initialize(PluginContext context) throws PluginException {
        this.context = context;
        context.log("Custom plugin initialized");
        
        // Access server configuration
        int port = context.config().port();
        
        // Access other plugins
        RoutingPlugin routing = context.getPlugin("routing", RoutingPlugin.class);
    }
    
    @Override
    public void start() throws PluginException {
        context.log("Custom plugin started");
    }
    
    @Override
    public void stop() throws PluginException {
        context.log("Custom plugin stopped");
    }
}
```

### Register Plugin

```java
NovaServer server = new NovaServer(8080);

// Register custom plugin
server.use(new CustomPlugin());

server.start();
```

### Plugin Priority

Plugins execute in priority order:

1. **HIGHEST** (0) - Critical infrastructure
2. **VERY_HIGH** (10) - Security, logging
3. **HIGH** (20) - Protocol detection, SSL
4. **ABOVE_NORMAL** (40) - Authentication
5. **NORMAL** (50) - Middleware (default)
6. **BELOW_NORMAL** (60) - Routing
7. **LOW** (80) - Cleanup, monitoring
8. **VERY_LOW** (90) - Final processing
9. **LOWEST** (100) - Last execution

---

## 📊 Connection Management

NovaServer includes a thread-safe connection pool:

```java
// Connection pool automatically manages:
// - Maximum concurrent connections
// - Connection acquisition/release
// - Graceful degradation when pool is full

// Configure via NovaConfig
NovaConfig config = NovaConfig.builder()
    .maxConnections(5000)  // Max 5000 concurrent connections
    .build();

NovaServer server = new NovaServer(config);
```

When max connections reached:
- New connections receive `503 Service Unavailable`
- Existing connections continue normally
- Pool automatically releases on connection close

---

## 📚 Examples

NovaServer includes 5 comprehensive examples:

### 1. Hello World (`Example1_HelloWorld.java`)
- Basic server setup
- Simple GET routes
- JSON responses
- Error handling

### 2. RESTful API (`Example2_RestfulAPI.java`)
- CRUD operations
- Path parameters
- Query parameters
- In-memory storage

### 3. Middleware (`Example3_Middleware.java`)
- Request logging
- CORS middleware
- Authentication middleware
- Protected routes

### 4. WebSocket Chat (`Example4_WebSocketChat.java`)
- Real-time chat server
- Broadcasting to clients
- Connection management
- Interactive HTML client

### 5. Protocol Detection (`Example5_ProtocolDetection.java`)
- Custom binary protocols
- Magic byte detection
- Multiple protocol handlers
- HTTP + custom protocols on same port

---

## 🎯 Use Cases

NovaServer is perfect for:

- **Microservices** - Lightweight HTTP services
- **APIs** - RESTful and WebSocket APIs
- **Real-time Apps** - Chat, notifications, live updates
- **IoT Devices** - Custom protocol support
- **Prototyping** - Quick server setup
- **Learning** - Study server architecture
- **Testing** - Mock servers for testing

---

## 🏗️ Architecture

### Component Diagram

```
┌─────────────────────────────────────────┐
│          NovaServer (Core)              │
│  - Connection Pool                      │
│  - Thread Management (Virtual/Platform) │
│  - Plugin Manager                       │
│  - Protocol Detection                   │
└────────────┬────────────────────────────┘
             │
    ┌────────┴────────┐
    │  Plugin System  │
    └────────┬────────┘
             │
     ┌───────┴──────────────────────┐
     │                              │
┌────▼────┐                   ┌─────▼─────┐
│ Default │                   │ Optional  │
│ Plugins │                   │ Plugins   │
└─────────┘                   └───────────┘
     │                              │
  ┌──┴──┐                      ┌────┴────┐
  │     │                      │         │
┌─▼─┐ ┌─▼─┐                  ┌─▼─┐     ┌─▼─┐
│Rou│ │Mid│                  │Web│     │SSL│
│ting│ │dle│                  │Sock│     │   │
└───┘ └───┘                  └───┘     └───┘
```

### Request Processing Flow

```
1. Socket Accept
   ↓
2. Connection Pool Check
   ↓
3. Protocol Detection (magic bytes)
   ├─→ HTTP: Continue to step 4
   └─→ Custom: Protocol Handler (end)
   ↓
4. HTTP Parse
   ↓
5. WebSocket Upgrade Check
   ├─→ Yes: WebSocket Handler (keep-alive)
   └─→ No: Continue to step 6
   ↓
6. Middleware Chain
   ↓
7. Route Matching
   ↓
8. Response Send
   ↓
9. Connection Close/Release
```

---

## 🔧 Advanced Topics

### Virtual Threads (Java 21+)

```java
// Automatically uses virtual threads if available
NovaConfig config = NovaConfig.builder()
    .useVirtualThreads(true)
    .build();

// Each request gets its own virtual thread
// Scales to millions of concurrent connections
```

### Custom Error Handling

```java
server.routing().get("/api/data", (req, res) -> {
    try {
        // Your logic
        res.json(getData());
    } catch (Exception e) {
        res.status(500).json(
            "{\"error\":\"" + e.getMessage() + "\"}"
        );
    }
});
```

### Request Body Parsing

```java
server.routing().post("/api/users", (req, res) -> {
    String body = req.body();
    
    // JSON parsing (use library like Gson/Jackson)
    // User user = gson.fromJson(body, User.class);
    
    // Simple extraction (for demo)
    String name = extractField(body, "name");
    String email = extractField(body, "email");
    
    res.status(201).json("{\"id\":1,\"name\":\"" + name + "\"}");
});
```

### Static File Serving

```java
server.routing().get("/files/:filename", (req, res) -> {
    String filename = req.getPathParam("filename");
    File file = new File("public/" + filename);
    
    if (file.exists() && file.isFile()) {
        res.sendFile(file);
    } else {
        res.status(404).send("File not found");
    }
});
```

---

## 📝 HTTP Status Codes

NovaServer includes all standard HTTP status codes:

```java
// 2xx Success
HTTPStatus.OK                    // 200
HTTPStatus.CREATED               // 201
HTTPStatus.ACCEPTED              // 202
HTTPStatus.NO_CONTENT            // 204

// 3xx Redirection
HTTPStatus.MOVED_PERMANENTLY     // 301
HTTPStatus.FOUND                 // 302
HTTPStatus.NOT_MODIFIED          // 304

// 4xx Client Error
HTTPStatus.BAD_REQUEST           // 400
HTTPStatus.UNAUTHORIZED          // 401
HTTPStatus.FORBIDDEN             // 403
HTTPStatus.NOT_FOUND             // 404
HTTPStatus.METHOD_NOT_ALLOWED    // 405
HTTPStatus.PAYLOAD_TOO_LARGE     // 413

// 5xx Server Error
HTTPStatus.INTERNAL_SERVER_ERROR // 500
HTTPStatus.NOT_IMPLEMENTED       // 501
HTTPStatus.SERVICE_UNAVAILABLE   // 503
```

---

## 🧪 Testing

### Manual Testing

```bash
# Basic HTTP
curl http://localhost:8080/

# With headers
curl -H "Authorization: Bearer token123" http://localhost:8080/api/data

# POST request
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name":"John","email":"john@example.com"}'

# WebSocket (using wscat)
npm install -g wscat
wscat -c ws://localhost:8080/chat

# Custom protocol
echo -n "NOVAHello" | nc localhost 8080
```

### Unit Testing Example

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NovaServerTest {
    @Test
    void testServerStartStop() throws Exception {
        NovaServer server = new NovaServer(9999);
        server.start();
        assertTrue(server.isRunning());
        
        server.stop();
        assertFalse(server.isRunning());
    }
    
    @Test
    void testRouting() throws Exception {
        NovaServer server = new NovaServer(9999);
        AtomicBoolean called = new AtomicBoolean(false);
        
        server.routing().get("/test", (req, res) -> {
            called.set(true);
            res.send("OK");
        });
        
        server.start();
        
        // Make HTTP request to /test
        // Assert called.get() == true
        
        server.stop();
    }
}
```

---

## 🚧 Limitations & Known Issues

1. **HTTP/2 Not Supported** - Currently only HTTP/1.1
2. **No Built-in Template Engine** - Use external libraries
3. **No Built-in JSON Parser** - Use Gson, Jackson, etc.
4. **Single Port** - One server instance per port
5. **No Clustering** - Single-instance deployment

---

## 🛠️ Troubleshooting

### Port Already in Use

```java
// Change port
NovaServer server = new NovaServer(8081);
```

### Too Many Connections

```java
// Increase connection limit
NovaConfig config = NovaConfig.builder()
    .maxConnections(20000)
    .build();
```

### Socket Timeout

```java
// Increase timeout
NovaConfig config = NovaConfig.builder()
    .socketTimeout(60000)  // 60 seconds
    .build();
```

### Virtual Threads Not Working

```java
// Ensure Java 21+
java -version

// Explicitly enable
NovaConfig config = NovaConfig.builder()
    .useVirtualThreads(true)
    .build();
```

---

## 📖 API Reference

### NovaServer

```java
NovaServer(int port)
NovaServer(NovaConfig config)

NovaServer start()
void stop()
void await()
boolean isRunning()

<T extends Plugin> NovaServer use(T plugin)
<T extends Plugin> T getPlugin(String id, Class<T> type)
boolean hasPlugin(String pluginId)

RoutingPlugin routing()
MiddlewarePlugin middleware()

NovaConfig getConfig()
PluginManager getPluginManager()
```

### HTTPRequest

```java
HTTPMethod method()
String path()
String body()

String getHeader(String name)
String getQueryParam(String name)
String getPathParam(String name)
String getCookie(String name)

Map<String, String> headers()
Map<String, String> queryParams()
Map<String, String> pathParams()

String clientAddress()
boolean isWebSocketUpgrade()
```

### HTTPResponse

```java
HTTPResponse status(int code)
HTTPResponse status(HTTPStatus status)
HTTPResponse setHeader(String name, String value)
HTTPResponse contentType(String type)
HTTPResponse cookie(Cookie cookie)
HTTPResponse cookie(String name, String value)

void send(String content)
void html(String html)
void json(String json)
void send(String content, String contentType)
void sendFile(File file)
void sendFile(File file, String filename)

boolean isSent()
```

---

## 🎓 Best Practices

1. **Use Virtual Threads** - Enable for high concurrency (Java 21+)
2. **Configure Timeouts** - Prevent hanging connections
3. **Limit Request Size** - Protect against large payloads
4. **Use Middleware** - Keep route handlers clean
5. **Handle Errors** - Always catch exceptions in routes
6. **Close Resources** - Use try-with-resources for files
7. **Validate Input** - Check parameters and body
8. **Use HTTPS** - Enable SSL for production
9. **Log Requests** - Use middleware for logging
10. **Test Thoroughly** - Unit and integration tests

---

## 📄 License

MIT License - see [LICENSE](LICENSE) file for details

---

## 🤝 Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

---

## 👥 Authors

- **Your Name** - *Initial work*

---

## 🙏 Acknowledgments

- Inspired by Express.js, Koa.js, and Javalin
- Built with Java 21 features
- Community feedback and contributions

---

## 📞 Support

- 📧 Email: support@novaserver.dev
- 💬 Discord: [NovaServer Community](https://discord.gg/novaserver)
- 🐛 Issues: [GitHub Issues](https://github.com/yourusername/novaserver/issues)
- 📚 Docs: [Full Documentation](https://docs.novaserver.dev)

---

**Made with ❤️ using Java**
