# 🚀 NovaServer v3.1

**A modern, lightweight, plugin-based HTTP server framework for Java**

NovaServer is a high-performance, extensible web server framework built from scratch in pure Java. It features a clean plugin architecture, zero external dependencies (except for optional features), and support for modern Java features like virtual threads and records.

## ✨ Features

### Core Features
- 🔌 **Plugin-Based Architecture** - Extend functionality through clean, composable plugins
- ⚡ **High Performance** - Virtual threads support (Java 21+) for massive concurrency
- 🎯 **Zero Dependencies** - Core framework has no external dependencies
- 🔒 **Type-Safe** - Leverages Java records for immutable, type-safe configurations
- 🧵 **Concurrent** - Thread-safe design using modern Java concurrency primitives

### Built-in Plugins
- 🛣️ **Routing** - Express-like routing with path parameters (`:id`)
- 🔄 **Middleware** - Request/response pipeline with chainable middleware
- 🔌 **WebSocket** - Full WebSocket protocol support for real-time communication
- 🔐 **SSL/TLS** - HTTPS support with configurable cipher suites
- 📡 **Protocol Detection** - Custom protocol handling via magic bytes

### HTTP Features
- ✅ All standard HTTP methods (GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD)
- 📊 JSON responses with built-in utilities
- 🍪 Cookie parsing and setting with security options
- 🎯 Query parameter parsing
- 📝 Request body parsing (text, JSON)
- 🔍 Path parameter extraction (`/users/:id`)
- 📄 File downloads with custom headers

## 📦 Quick Start

### Basic Hello World

```java
import com.nova.framework.NovaServer;

public class HelloWorld {
    public static void main(String[] args) throws Exception {
        NovaServer server = new NovaServer(8080);
        
        server.routing().get("/", (req, res) -> {
            res.send("Hello, World!");
        });
        
        server.start().await();
    }
}
```

### RESTful API

```java
NovaServer server = new NovaServer(8080);

// List all users
server.routing().get("/api/users", (req, res) -> {
    res.json("[{\"id\":1,\"name\":\"Alice\"}]");
});

// Get user by ID
server.routing().get("/api/users/:id", (req, res) -> {
    String id = req.getPathParam("id");
    res.json("{\"id\":" + id + ",\"name\":\"User " + id + "\"}");
});

// Create user
server.routing().post("/api/users", (req, res) -> {
    String body = req.body();
    // Process body...
    res.status(201).json("{\"success\":true}");
});

server.start();
```

### Middleware

```java
NovaServer server = new NovaServer(8080);

// Logging middleware
server.middleware().use((req, res) -> {
    System.out.println(req.method() + " " + req.path());
    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
});

// CORS middleware
server.middleware().use((req, res) -> {
    res.setHeader("Access-Control-Allow-Origin", "*");
    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
});

// Authentication middleware
server.middleware().use((req, res) -> {
    if (req.path().startsWith("/api/protected")) {
        String token = req.getHeader("Authorization");
        if (token == null) {
            res.status(401).json("{\"error\":\"Unauthorized\"}");
            return MiddlewarePlugin.MiddlewareResult.STOP;
        }
    }
    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
});

server.start();
```

### WebSocket

```java
NovaServer server = new NovaServer(8080);

// Register WebSocket plugin
WebSocketPlugin ws = new WebSocketPlugin();
server.use(ws);

// WebSocket endpoint
ws.websocket("/chat", client -> {
    // Handle new connection
    System.out.println("Client connected: " + client.getId());
    
    // Handle messages
    client.onMessage(msg -> {
        System.out.println("Received: " + msg);
        client.send("Echo: " + msg);
    });
    
    // Handle disconnect
    client.onClose(reason -> {
        System.out.println("Client disconnected");
    });
});

server.start();
```

## 🏗️ Architecture

### Plugin System

NovaServer uses a sophisticated plugin architecture that allows clean separation of concerns:

```java
public interface Plugin {
    String id();                           // Unique plugin identifier
    PluginPriority priority();             // Execution priority
    Set<String> dependencies();            // Plugin dependencies
    void initialize(PluginContext ctx);    // Initialize with context
    void start();                          // Start plugin
    void stop();                           // Stop plugin
}
```

### Default Plugins

Two plugins are automatically registered:
1. **RoutingPlugin** - HTTP routing (priority: BELOW_NORMAL)
2. **MiddlewarePlugin** - Request middleware (priority: NORMAL)

### Custom Plugins

Create custom plugins by extending `BasePlugin`:

```java
public class MyPlugin extends BasePlugin {
    @Override
    public String id() { return "my-plugin"; }
    
    @Override
    public PluginPriority priority() {
        return PluginPriority.HIGH;
    }
    
    @Override
    public void initialize(PluginContext context) {
        context.log("MyPlugin initialized");
    }
}

// Register the plugin
server.use(new MyPlugin());
```

## ⚙️ Configuration

### Server Configuration

```java
NovaConfig config = NovaConfig.builder()
    .port(8080)
    .maxConnections(10000)
    .maxRequestSize(10 * 1024 * 1024)  // 10MB
    .socketTimeout(30000)               // 30 seconds
    .workerThreads(0)                   // Auto (CPU cores * 2)
    .useVirtualThreads(true)            // Java 21+ virtual threads
    .build();

NovaServer server = new NovaServer(config);
```

### SSL/TLS Configuration

```java
SSLPlugin ssl = new SSLPlugin();
ssl.configure("keystore.jks", "password");
server.use(ssl);
```

## 📚 Examples

The project includes 4 comprehensive examples:

### 1. Hello World (`Example1_HelloWorld.java`)
- Basic server setup
- Multiple endpoints
- JSON and HTML responses
- Server information endpoint

### 2. RESTful API (`Example2_RestfulAPI.java`)
- Full CRUD operations
- Path parameters (`:id`)
- Query parameters (`?search=name`)
- Proper HTTP status codes
- In-memory data storage

### 3. Middleware (`Example3_Middleware.java`)
- Request logging
- CORS handling
- Authentication/Authorization
- Protected routes
- Public vs private endpoints

### 4. WebSocket Chat (`Example4_WebSocketChat.java`)
- Real-time bidirectional communication
- Chat room functionality
- Multiple client connections
- Broadcasting messages
- HTML chat client included

## 🔧 API Reference

### NovaServer

```java
// Create server
NovaServer server = new NovaServer(8080);
NovaServer server = new NovaServer(config);

// Register plugins
server.use(new MyPlugin());

// Access plugins
server.routing()    // Get routing plugin
server.middleware() // Get middleware plugin

// Lifecycle
server.start()      // Start server
server.stop()       // Stop server
server.await()      // Wait for shutdown
```

### Routing

```java
// Define routes
server.routing().get("/path", handler);
server.routing().post("/path", handler);
server.routing().put("/path", handler);
server.routing().delete("/path", handler);

// Path parameters
server.routing().get("/users/:id", (req, res) -> {
    String id = req.getPathParam("id");
});

// Multiple parameters
server.routing().get("/posts/:postId/comments/:commentId", handler);
```

### Request (HTTPRequest)

```java
// HTTP basics
req.method()        // HTTPMethod enum
req.path()          // Request path
req.body()          // Request body as String
req.clientAddress() // Client IP address

// Headers
req.getHeader("Content-Type")
req.headers()       // All headers (Map)

// Parameters
req.getQueryParam("page")      // Query params (?page=1)
req.getPathParam("id")         // Path params (/users/:id)
req.queryParams()              // All query params (Map)
req.pathParams()               // All path params (Map)

// Cookies
req.getCookie("sessionId")

// WebSocket
req.isWebSocketUpgrade()
```

### Response (HTTPResponse)

```java
// Status
res.status(200)
res.status(HTTPStatus.OK)

// Headers
res.setHeader("Content-Type", "application/json")
res.contentType("application/json")

// Send responses
res.send("text")               // Plain text
res.html("<h1>HTML</h1>")      // HTML
res.json("{\"key\":\"value\"}") // JSON

// Cookies
res.cookie("name", "value")
res.cookie(Cookie.builder("name", "value")
    .maxAge(3600)
    .httpOnly(true)
    .secure(true)
    .build())

// Files
res.sendFile(new File("document.pdf"))
```

### Middleware

```java
server.middleware().use((req, res) -> {
    // Middleware logic
    
    // Continue to next middleware/route
    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
    
    // Stop processing (response already sent)
    return MiddlewarePlugin.MiddlewareResult.STOP;
});
```

## 🚀 Performance

### Benchmarks (Approximate)

- **Requests/second**: 50,000+ (simple endpoints, virtual threads)
- **Concurrent connections**: 10,000+ (configurable)
- **Latency**: Sub-millisecond for static responses
- **Memory**: ~50MB baseline (varies with connection count)

### Optimization Tips

1. **Use Virtual Threads** (Java 21+)
   ```java
   NovaConfig.builder().useVirtualThreads(true)
   ```

2. **Tune Connection Pool**
   ```java
   NovaConfig.builder().maxConnections(50000)
   ```

3. **Optimize Socket Settings**
   ```java
   NovaConfig.builder()
       .socketTimeout(5000)      // Lower timeout for faster turnover
       .maxRequestSize(1_000_000) // Limit request size
   ```

4. **Use Middleware Wisely**
   - Keep middleware logic fast
   - Avoid blocking operations
   - Use `STOP` to short-circuit when appropriate

## 🔐 Security

### Built-in Security Features

1. **Header Injection Prevention**
   - Headers are validated to prevent CRLF injection
   - Cookie values are URL-encoded

2. **Request Size Limits**
   - Configurable max request size
   - Automatic rejection of oversized requests

3. **Connection Limits**
   - Maximum concurrent connection enforcement
   - Automatic rejection when limit reached

4. **Cookie Security**
   - HttpOnly flag (default: true)
   - Secure flag for HTTPS
   - SameSite attribute support

### Security Best Practices

```java
// 1. Use HTTPS in production
SSLPlugin ssl = new SSLPlugin();
ssl.configure("keystore.jks", "password");
server.use(ssl);

// 2. Implement authentication middleware
server.middleware().use((req, res) -> {
    if (req.path().startsWith("/api/")) {
        String token = req.getHeader("Authorization");
        if (!isValidToken(token)) {
            res.status(401).json("{\"error\":\"Unauthorized\"}");
            return MiddlewarePlugin.MiddlewareResult.STOP;
        }
    }
    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
});

// 3. Set secure cookies
res.cookie(Cookie.builder("session", token)
    .httpOnly(true)
    .secure(true)
    .sameSite("Strict")
    .build());

// 4. Validate input
String id = req.getPathParam("id");
if (!isValidId(id)) {
    res.status(400).json("{\"error\":\"Invalid ID\"}");
    return;
}
```

## 🧪 Testing

### Unit Testing Routes

```java
@Test
public void testHelloEndpoint() throws Exception {
    NovaServer server = new NovaServer(8080);
    server.routing().get("/hello", (req, res) -> {
        res.send("Hello!");
    });
    server.start();
    
    // Use HTTP client to test
    HttpClient client = HttpClient.newHttpClient();
    HttpResponse<String> response = client.send(
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8080/hello"))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );
    
    assertEquals(200, response.statusCode());
    assertEquals("Hello!", response.body());
    
    server.stop();
}
```

## 📖 Documentation

### Package Structure

```
com.nova.framework
├── NovaServer.java              # Main server class
├── config
│   └── NovaConfig.java          # Server configuration
├── core
│   └── ConnectionPool.java      # Connection management
├── http
│   ├── Cookie.java              # Cookie handling
│   ├── HTTPMethod.java          # HTTP methods enum
│   ├── HTTPParser.java          # Request parser
│   ├── HTTPRequest.java         # Request object
│   ├── HTTPResponse.java        # Response builder
│   └── HTTPStatus.java          # Status codes
├── plugin
│   ├── BasePlugin.java          # Plugin base class
│   ├── Plugin.java              # Plugin interface
│   ├── PluginContext.java       # Plugin context
│   ├── PluginException.java     # Plugin errors
│   ├── PluginManager.java       # Plugin management
│   ├── PluginPriority.java      # Execution priority
│   └── PluginState.java         # Lifecycle states
└── plugins
    ├── MiddlewarePlugin.java    # Middleware support
    ├── ProtocolDetectionPlugin.java  # Custom protocols
    ├── RoutingPlugin.java       # HTTP routing
    ├── SSLPlugin.java           # SSL/TLS support
    ├── WebSocketPlugin.java     # WebSocket support
    ├── routing
    │   ├── RouteHandler.java    # Route handler interface
    │   └── Router.java          # Route matcher
    └── websocket
        └── WebSocketConnection.java  # WebSocket client
```

## 🤝 Contributing

Contributions are welcome! Here's how you can help:

1. **Report Bugs** - Open an issue with details
2. **Suggest Features** - Describe your use case
3. **Submit PRs** - Follow the existing code style
4. **Write Plugins** - Extend NovaServer with new capabilities

### Plugin Development Guidelines

1. Extend `BasePlugin` for convenience
2. Use descriptive plugin IDs
3. Declare dependencies explicitly
4. Handle errors gracefully
5. Add logging for debugging
6. Document your plugin

## 📄 License

This project is open source. See LICENSE file for details.

## 🙏 Acknowledgments

Built with inspiration from:
- Express.js (Node.js)
- Javalin (Java)
- Netty (Java)

## 📞 Support

- **Issues**: GitHub Issues
- **Documentation**: This README
- **Examples**: `/examples` directory

## 🗺️ Roadmap

### Version 3.1 (Planned)
- [ ] HTTP/2 support
- [ ] Request/response compression
- [ ] Rate limiting plugin
- [ ] Session management plugin
- [ ] Built-in JSON library integration

### Version 3.2 (Future)
- [ ] GraphQL support
- [ ] gRPC support
- [ ] Metrics and monitoring plugin
- [ ] Admin dashboard
- [ ] Hot reload during development

## 📊 Status

- ✅ Core server - **Stable**
- ✅ Routing plugin - **Stable**
- ✅ Middleware plugin - **Stable**
- ✅ WebSocket plugin - **Stable**
- ⚠️ SSL plugin - **Beta**
- ⚠️ Protocol detection - **Beta**

---

**Made with ❤️ by the NovaServer team**

*Start building modern Java web applications today!*
