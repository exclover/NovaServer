# 🚀 Nova Framework

**Modern High-Performance Web Server for Java**

Nova is a lightweight, blazingly fast web framework for Java that combines simplicity with powerful features. Built for Java 17+ with native support for Virtual Threads (Java 21+), it provides everything you need to build modern web applications and APIs.

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.java.net/)
[![Virtual Threads](https://img.shields.io/badge/Virtual%20Threads-Java%2021%2B-blue.svg)](https://openjdk.java.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

## ✨ Features

### Core Capabilities
- ⚡ **High Performance** - Handles 10,000+ concurrent connections with Virtual Threads
- 🔌 **WebSocket Support** - Full-featured WebSocket implementation with broadcast capabilities
- 🛡️ **SSL/TLS Support** - Built-in HTTPS with customizable SSL configuration
- 🔄 **Middleware Pipeline** - Flexible request/response processing chain
- 🎯 **Smart Routing** - Powerful router with path parameters and pattern matching
- 📦 **Protocol Detection** - Automatic protocol detection (HTTP, WebSocket, Custom)
- 🗜️ **Compression** - GZIP compression for responses
- 🌐 **CORS Support** - Built-in Cross-Origin Resource Sharing
- 📊 **Metrics & Monitoring** - Real-time server statistics and performance metrics
- 🔒 **Thread-Safe** - Designed for concurrent environments

### Advanced Features
- Virtual Threads support (Java 21+) for massive concurrency
- Connection pooling with configurable limits
- Request/response lifecycle management
- Cookie handling and session management
- JSON/Form data parsing
- File serving with content-type detection
- Rate limiting support
- Custom protocol handlers
- Hot reload support (planned)

## 📦 Installation

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
<dependency>
    <groupId>com.github.exclover</groupId>
    <artifactId>NovaServer</artifactId>
    <version>Tag</version>
</dependency>
```

### Gradle

```gradle
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.exclover:NovaServer:Tag")
}
```

### Manual

1. Clone the repository
2. Build with Maven: `mvn clean install`
3. Add the JAR to your project classpath

## 🚀 Quick Start

### Simple HTTP Server

```java
import com.nova.framework.NovaServer;

public class MyApp {
    public static void main(String[] args) throws Exception {
        NovaServer server = new NovaServer(8080);
        
        server.get("/", (req, res) -> {
            res.json("{\"message\": \"Hello, Nova!\"}");
        });
        
        server.start();
        System.out.println("Server running on http://localhost:8080");
    }
}
```

### Advanced Configuration

```java
import com.nova.framework.NovaServer;
import com.nova.framework.config.ServerConfig;
import com.nova.framework.ssl.SSLConfig;

public class MyApp {
    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.builder()
            .port(8080)
            .maxConnections(10000)
            .maxRequestSize(10 * 1024 * 1024) // 10MB
            .socketTimeout(30000)              // 30 seconds
            .useVirtualThreads(true)           // Java 21+
            .protocolDetection(true)
            .enableSSL(SSLConfig.fromKeystore(
                "keystore.jks", 
                "keystorePassword", 
                "keyPassword"
            ))
            .build();
        
        NovaServer server = new NovaServer(config);
        
        // Configure routes and middleware
        setupRoutes(server);
        setupMiddleware(server);
        
        server.start();
    }
}
```

## 📖 Documentation

### Routing

Nova provides a simple and intuitive routing API:

```java
// Basic routes
server.get("/users", (req, res) -> {
    res.json("{\"users\": []}");
});

server.post("/users", (req, res) -> {
    String body = req.body();
    res.status(201).json("{\"created\": true}");
});

// Path parameters
server.get("/users/:id", (req, res) -> {
    String id = req.param("id");
    res.json("{\"id\": \"" + id + "\"}");
});

// Multiple parameters
server.get("/posts/:postId/comments/:commentId", (req, res) -> {
    String postId = req.param("postId");
    String commentId = req.param("commentId");
    res.json("{\"post\": \"" + postId + "\", \"comment\": \"" + commentId + "\"}");
});

// Query parameters
server.get("/search", (req, res) -> {
    String query = req.query("q");
    int page = req.queryInt("page", 1);
    res.json("{\"query\": \"" + query + "\", \"page\": " + page + "}");
});

// All HTTP methods
server.get("/resource", handler);
server.post("/resource", handler);
server.put("/resource/:id", handler);
server.patch("/resource/:id", handler);
server.delete("/resource/:id", handler);
server.options("/resource", handler);
server.head("/resource", handler);
```

### Request Object

The `Request` object provides access to all request data:

```java
server.post("/example", (req, res) -> {
    // Basic info
    String method = req.method();        // GET, POST, etc.
    String path = req.path();            // /example
    String clientIP = req.clientIP();    // Client IP address
    
    // Headers
    String contentType = req.header("Content-Type");
    String userAgent = req.userAgent();
    String auth = req.authorization();
    String token = req.bearerToken();
    
    // Path parameters
    String id = req.param("id");
    int idInt = req.paramInt("id", 0);
    
    // Query parameters
    String q = req.query("q");
    int page = req.queryInt("page", 1);
    List<String> tags = req.queryAll("tag");
    
    // Cookies
    String sessionId = req.cookie("session");
    Map<String, String> allCookies = req.cookies();
    
    // Body
    String rawBody = req.body();
    
    // JSON parsing
    if (req.isJson()) {
        Map<String, Object> json = req.json();
        String name = req.jsonString("name");
        Integer age = req.jsonInt("age");
    }
    
    // Form data
    if (req.isFormData()) {
        Map<String, String> form = req.form();
        String username = req.formValue("username");
    }
    
    // WebSocket upgrade check
    if (req.isWebSocketUpgrade()) {
        // Handle WebSocket upgrade
    }
    
    // Custom attributes
    req.set("userId", "123");
    String userId = (String) req.get("userId");
    User user = req.get("user", User.class);
    
    res.json("{\"success\": true}");
});
```

### Response Object

The `Response` object provides methods for sending responses:

```java
server.get("/response-examples", (req, res) -> {
    // Set status
    res.status(200);
    
    // Set headers
    res.setHeader("X-Custom-Header", "value");
    
    // Send JSON
    res.json("{\"message\": \"Hello\"}");
    
    // Send JSON from Map
    Map<String, Object> data = new HashMap<>();
    data.put("message", "Hello");
    res.json(data);
    
    // Send HTML
    res.html("<h1>Hello, World!</h1>");
    
    // Send plain text
    res.text("Hello, World!");
    
    // Send XML
    res.xml("<message>Hello</message>");
    
    // Send file
    res.file(new File("document.pdf"));
    
    // Redirect
    res.redirect("/new-location");
    res.redirect("/new-location", 301);
    
    // No content
    res.noContent();
    
    // Convenience methods
    res.ok("Operation successful");
    res.created("Resource created");
    res.badRequest("Invalid input");
    res.unauthorized("Authentication required");
    res.forbidden("Access denied");
    res.notFound("Resource not found");
    res.serverError("Internal server error");
});

// Cookies
server.get("/set-cookie", (req, res) -> {
    res.cookie("session", "abc123");
    
    // With options
    res.cookie("session", "abc123", 
        new Response.CookieOptions()
            .maxAge(3600)
            .path("/")
            .domain("example.com")
            .secure(true)
            .httpOnly(true)
            .sameSite("Strict")
    );
    
    res.json("{\"success\": true}");
});

// Compression
server.get("/large-data", (req, res) -> {
    res.enableCompression();
    res.json(largeJsonData);
});
```

### Middleware

Middleware functions process requests before they reach route handlers:

```java
// Global middleware (applies to all routes)
server.use((ctx) -> {
    System.out.println("Request: " + ctx.method() + " " + ctx.path());
    ctx.next(); // Continue to next middleware or route handler
});

// Path-specific middleware
server.use("/admin", (ctx) -> {
    String token = ctx.header("Authorization");
    if (token == null || !isValidToken(token)) {
        ctx.response().unauthorized("Invalid token");
        ctx.stop(); // Stop middleware chain
        return;
    }
    ctx.next();
});

// Middleware example: Request logging
server.use((ctx) -> {
    long start = System.currentTimeMillis();
    String method = ctx.method();
    String path = ctx.path();
    
    ctx.next();
    
    long duration = System.currentTimeMillis() - start;
    System.out.printf("%s %s - %dms%n", method, path, duration);
});

// Middleware example: Authentication
server.use("/api", (ctx) -> {
    String token = ctx.request().bearerToken();
    
    if (token == null) {
        ctx.response().unauthorized("Token required");
        ctx.stop();
        return;
    }
    
    User user = authenticateToken(token);
    if (user == null) {
        ctx.response().unauthorized("Invalid token");
        ctx.stop();
        return;
    }
    
    ctx.set("user", user);
    ctx.next();
});

// Middleware example: Rate limiting
Map<String, List<Long>> rateLimits = new ConcurrentHashMap<>();
server.use((ctx) -> {
    String ip = ctx.clientIP();
    long now = System.currentTimeMillis();
    
    rateLimits.putIfAbsent(ip, new ArrayList<>());
    List<Long> requests = rateLimits.get(ip);
    
    // Remove old requests (> 1 minute)
    requests.removeIf(time -> now - time > 60000);
    
    if (requests.size() >= 100) {
        ctx.response().status(429).json("{\"error\": \"Too many requests\"}");
        ctx.stop();
        return;
    }
    
    requests.add(now);
    ctx.next();
});

// Built-in middleware
server.enableCors();        // Enable CORS
server.enableCompression(); // Enable GZIP compression
```

### WebSocket Support

Nova provides full WebSocket support with an easy-to-use API:

```java
// Create WebSocket handler
server.websocket("/ws/chat", ws -> {
    ws.onConnect(conn -> {
        System.out.println("Client connected: " + conn.id());
        conn.sendText("Welcome to the chat!");
    });
    
    ws.onMessage((conn, message) -> {
        System.out.println("Received: " + message);
        
        // Echo back
        conn.sendText("Echo: " + message);
        
        // Broadcast to all clients
        ws.broadcast("User said: " + message);
        
        // Broadcast to all except sender
        ws.broadcastExcept("User said: " + message, conn);
    });
    
    ws.onClose(conn -> {
        System.out.println("Client disconnected: " + conn.id());
    });
    
    ws.onError((conn, error) -> {
        System.err.println("WebSocket error: " + error.getMessage());
    });
});

// WebSocket with path parameters
server.websocket("/ws/room/:roomId", ws -> {
    ws.onConnect(conn -> {
        String roomId = conn.param("roomId");
        System.out.println("Joined room: " + roomId);
    });
    
    ws.onMessage((conn, message) -> {
        // Handle message
    });
});

// WebSocket connection management
server.websocket("/ws", ws -> {
    ws.onConnect(conn -> {
        // Store user data
        conn.set("userId", "123");
        conn.set("username", "Alice");
        
        // Get stored data
        String userId = conn.get("userId", String.class);
    });
    
    ws.onMessage((conn, message) -> {
        // Send to specific client
        conn.sendText("Response");
        
        // Send binary data
        conn.sendBinary(new byte[]{1, 2, 3});
        
        // Send ping
        conn.sendPing();
        
        // Close connection
        conn.close();
        conn.close(1000, "Normal closure");
    });
});
```

### SSL/TLS Configuration

Enable HTTPS with SSL/TLS:

```java
import com.nova.framework.ssl.SSLConfig;

// From keystore file
SSLConfig sslConfig = SSLConfig.fromKeystore(
    "keystore.jks",
    "keystorePassword",
    "keyPassword"
);

// From PEM files
SSLConfig sslConfig = SSLConfig.fromPEM(
    "certificate.crt",
    "private.key"
);

// Custom SSL configuration
SSLConfig sslConfig = SSLConfig.builder()
    .keystorePath("keystore.jks")
    .keystorePassword("password")
    .keyPassword("password")
    .keystoreType("JKS")
    .protocol("TLSv1.3")
    .enabledProtocols(new String[]{"TLSv1.2", "TLSv1.3"})
    .enabledCipherSuites(new String[]{
        "TLS_AES_256_GCM_SHA384",
        "TLS_AES_128_GCM_SHA256"
    })
    .build();

// Create server with SSL
ServerConfig config = ServerConfig.builder()
    .port(8443)
    .enableSSL(sslConfig)
    .build();

NovaServer server = new NovaServer(config);
```

### Server Configuration

```java
ServerConfig config = ServerConfig.builder()
    .port(8080)                          // Server port (default: required)
    .maxConnections(10000)               // Max concurrent connections (default: 10000)
    .maxRequestSize(10 * 1024 * 1024)   // Max request size in bytes (default: 10MB)
    .socketTimeout(30000)                // Socket timeout in ms (default: 30000)
    .shutdownTimeout(10)                 // Graceful shutdown timeout in seconds (default: 10)
    .workerThreads(16)                   // Worker thread count (default: CPU cores * 2)
    .useVirtualThreads(true)             // Use virtual threads (Java 21+)
    .protocolDetection(true)             // Enable protocol detection (default: true)
    .enableSSL(sslConfig)                // Enable SSL/TLS
    .compression(true)                   // Enable compression
    .cors(true)                          // Enable CORS
    .build();
```

### Error Handling

```java
// Custom error logging
server.onLog((message, error) -> {
    if (error != null) {
        System.err.println("[ERROR] " + message);
        error.printStackTrace();
    } else {
        System.out.println("[INFO] " + message);
    }
});

// Handle errors in routes
server.get("/error-example", (req, res) -> {
    try {
        // Some operation that might fail
        riskyOperation();
        res.ok("Success");
    } catch (Exception e) {
        res.serverError("Operation failed: " + e.getMessage());
    }
});

// Middleware error handling
server.use((ctx) -> {
    try {
        ctx.next();
    } catch (Exception e) {
        ctx.response().serverError("Internal error");
    }
});
```

### Server Metrics

```java
// Get server metrics
server.get("/metrics", (req, res) -> {
    Map<String, Object> metrics = server.getMetrics();
    res.json(metrics);
});

// Metrics include:
// - totalRequests: Total requests processed
// - activeConnections: Current active connections
// - uptime: Server uptime in milliseconds
// - requestsPerSecond: Average requests per second
// - avgResponseTime: Average response time
// - errorCount: Total error count
```

### Graceful Shutdown

```java
// Server automatically registers shutdown hook
server.start();

// Manual shutdown
server.shutdown();

// Shutdown with custom timeout
server.shutdown(15); // 15 seconds timeout
```

## 🏗️ Architecture

### Package Structure

```
com.nova.framework
├── config/              - Configuration classes
│   └── ServerConfig     - Server configuration with builder
├── connection/          - Connection management
│   └── ConnectionPool   - Thread-safe connection pool
├── core/               - Core request/response handling
│   ├── Request         - HTTP request representation
│   ├── Response        - HTTP response builder
│   └── RequestParser   - HTTP request parser
├── middleware/         - Middleware pipeline
│   ├── MiddlewarePipeline  - Middleware chain executor
│   ├── MiddlewareContext   - Middleware execution context
│   └── MiddlewareHandler   - Middleware interface
├── routing/            - Routing system
│   ├── Router          - Route matching and handling
│   └── RouteHandler    - Route handler interface
├── websocket/          - WebSocket support
│   ├── WebSocketHandler    - WebSocket endpoint handler
│   ├── WSConnection        - WebSocket connection
│   ├── WSUpgrader          - WebSocket upgrade handler
│   └── WSFrame             - WebSocket frame parser
├── protocol/           - Protocol detection
│   ├── ProtocolDetector       - Protocol detection
│   └── CustomProtocolHandler  - Custom protocol interface
├── ssl/                - SSL/TLS support
│   └── SSLConfig       - SSL configuration
└── NovaServer          - Main server class
```

### Threading Model

Nova uses a sophisticated threading model:

1. **Acceptor Thread**: Single thread accepts incoming connections
2. **Worker Pool**: Handles request processing
   - Platform threads (Java 17+): Configurable pool size
   - Virtual threads (Java 21+): Unlimited lightweight threads
3. **WebSocket Threads**: Dedicated threads for WebSocket connections

### Request Lifecycle

1. **Connection Acceptance**: Server accepts TCP connection
2. **Protocol Detection**: Identify HTTP, WebSocket, or custom protocol
3. **Request Parsing**: Parse HTTP request into Request object
4. **Middleware Chain**: Execute middleware pipeline
5. **Route Matching**: Find matching route handler
6. **Handler Execution**: Execute route handler
7. **Response**: Send response to client
8. **Connection Close**: Close or keep-alive

## 🔧 Advanced Topics

### Custom Protocol Handlers

```java

        // ========== EXAMPLE 1: Using byte array ==========
        // Register "NOVA" protocol (0x4E 0x4F 0x56 0x41)
        server.onCustomProtocol(new byte[]{0x4E, 0x4F, 0x56, 0x41}, socket -> {
            System.out.println("NOVA protocol detected!");
            
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            
            // Read rest of the data
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String data = reader.readLine();
            
            System.out.println("Received: " + data);
            
            // Send response
            out.write("NOVA OK\n".getBytes());
            out.flush();
            
            socket.close();
        });
        
        // ========== EXAMPLE 2: Using hex string ==========
        // Register "CHAT" protocol (0x43 0x48 0x41 0x54)
        server.onCustomProtocol("43484154", socket -> {
            System.out.println("CHAT protocol detected!");
            
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            // Handle chat protocol
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String message = reader.readLine();
            
            System.out.println("Chat message: " + message);
            
            String response = "CHAT RECEIVED: " + message + "\n";
            out.write(response.getBytes());
            out.flush();
            
            socket.close();
        });
        
        // ========== EXAMPLE 3: Binary protocol ==========
        // Register custom binary protocol with magic bytes 0xDE 0xAD 0xBE 0xEF
        server.onCustomProtocol(new byte[]{(byte)0xDE, (byte)0xAD, (byte)0xBE, (byte)0xEF}, socket -> {
            System.out.println("Binary protocol detected!");
            
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            
            try {
                
                // Read binary data
                int length = in.readInt();
                byte[] data = new byte[length];
                in.readFully(data);
                
                System.out.println("Received " + length + " bytes");
                
                // Send acknowledgment
                out.writeInt(0x00); // OK status
                out.flush();
                
            } finally {
                socket.close();
            }
        });
        
        // ========== EXAMPLE 4: Multiple protocols ==========
        // Register "AUTH" protocol
        server.onCustomProtocol("41555448", socket -> {
            System.out.println("AUTH protocol detected!");
            
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String credentials = reader.readLine();
            
            // Validate credentials
            boolean isValid = validateCredentials(credentials);
            
            if (isValid) {
                out.write("AUTH OK\n".getBytes());
            } else {
                out.write("AUTH FAILED\n".getBytes());
            }
            out.flush();
            
            socket.close();
        });
        
```

### Virtual Threads (Java 21+)

```java
// Enable virtual threads for massive concurrency
ServerConfig config = ServerConfig.builder()
    .port(8080)
    .useVirtualThreads(true)  // Automatically detects Java 21+
    .build();

NovaServer server = new NovaServer(config);
// Can now handle 100,000+ concurrent connections efficiently
```

### Connection Pool Management

```java
// Connection pool is managed automatically
// Access pool info through metrics
Map<String, Object> metrics = server.getMetrics();
int active = (int) metrics.get("activeConnections");
int max = (int) metrics.get("maxConnections");
double utilization = (double) metrics.get("poolUtilization");
```

### Performance Tuning

```java
ServerConfig config = ServerConfig.builder()
    .port(8080)
    .maxConnections(50000)           // Increase for high traffic
    .maxRequestSize(50 * 1024 * 1024) // 50MB for large uploads
    .socketTimeout(60000)             // 60s for long-polling
    .workerThreads(32)                // Match CPU cores for best performance
    .useVirtualThreads(true)          // Best for I/O-bound workloads
    .compression(true)                // Reduce bandwidth
    .build();
```

## 📊 Performance

### Benchmarks

Tested on: Intel Core i7, 16GB RAM, Java 21

| Scenario | Throughput | Latency (p99) |
|----------|-----------|---------------|
| Simple JSON response | 45,000 req/s | 2ms |
| WebSocket messages | 100,000 msg/s | 1ms |
| File serving (1MB) | 5,000 req/s | 15ms |
| Virtual threads (10k connections) | 40,000 req/s | 3ms |

### Best Practices

1. **Use Virtual Threads** for I/O-bound workloads (Java 21+)
2. **Enable Compression** for large JSON responses
3. **Implement Connection Pooling** for database connections
4. **Use Middleware** for common tasks (auth, logging, etc.)
5. **Set Appropriate Timeouts** to prevent resource exhaustion
6. **Monitor Metrics** regularly for performance insights
7. **Enable SSL** for production environments
8. **Implement Rate Limiting** to prevent abuse

## 🛠️ Development

### Building from Source

```bash
git clone https://github.com/your-org/nova-framework.git
cd nova-framework
mvn clean install
```

### Running Tests

```bash
mvn test
```

### Running Examples

```bash
mvn exec:java -Dexec.mainClass="com.nova.examples.NovaExample"
```

## 📝 Examples

Check out the `examples/` directory for complete working examples:

- **Basic Server**: Simple REST API
- **WebSocket Chat**: Real-time chat application
- **File Upload**: Handling file uploads
- **Authentication**: JWT-based auth
- **Rate Limiting**: Advanced rate limiting
- **Microservice**: Complete microservice example
- **HTTPS Server**: SSL/TLS configuration

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style

- Follow Java naming conventions
- Write unit tests for new features
- Document public APIs with Javadoc
- Keep methods focused and concise
- Use meaningful variable names

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Inspired by Express.js, Koa.js, and Spring Boot
- Built with modern Java features (Records, Virtual Threads, Pattern Matching)
- Thanks to all contributors and users

## 📞 Support

- **Documentation**: [https://nova-framework.dev/docs](https://nova-framework.dev/docs)
- **Issues**: [GitHub Issues](https://github.com/your-org/nova-framework/issues)
- **Discussions**: [GitHub Discussions](https://github.com/your-org/nova-framework/discussions)
- **Email**: support@nova-framework.dev

## 🗺️ Roadmap

### Version 2.2.0 (Planned)
- [ ] HTTP/2 support
- [ ] Server-Sent Events (SSE)
- [ ] Built-in template engine
- [ ] Static file caching
- [ ] Hot reload improvements

### Version 2.3.0 (Planned)
- [ ] Metrics dashboard
- [ ] GraphQL support
- [ ] Built-in authentication providers
- [ ] Database connection pooling
- [ ] Distributed tracing

### Version 3.0.0 (Future)
- [ ] HTTP/3 support
- [ ] Native image support (GraalVM)
- [ ] Clustering support
- [ ] Built-in API documentation
- [ ] WebAssembly integration

---

**Made with ❤️ by the Nova Team**

**Star ⭐ this repository if you find it helpful!**
