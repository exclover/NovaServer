package com.nova.framework.examples;

import com.nova.framework.NovaServer;
import com.nova.framework.http.HTTPStatus;

/**
 * Example 1: Hello World
 * 
 * Demonstrates:
 * - Basic server setup
 * - Simple GET routes
 * - JSON responses
 * - Error handling
 */
public class Example1_HelloWorld {

    public static void main(String[] args) {
        try {
            // Create server
            NovaServer server = new NovaServer(8080);

            // Root endpoint
            server.routing().get("/", (req, res) -> {
                res.html("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>NovaServer v3.0</title>
                        <style>
                            body { font-family: Arial, sans-serif; max-width: 800px; margin: 50px auto; padding: 20px; }
                            h1 { color: #333; }
                            .endpoint { background: #f4f4f4; padding: 10px; margin: 10px 0; border-radius: 5px; }
                            code { background: #e0e0e0; padding: 2px 5px; border-radius: 3px; }
                        </style>
                    </head>
                    <body>
                        <h1>🚀 Welcome to NovaServer v3.0</h1>
                        <p>A modern, plugin-based Java HTTP server framework</p>
                        
                        <h2>Available Endpoints:</h2>
                        <div class="endpoint">
                            <strong>GET /</strong> - This page
                        </div>
                        <div class="endpoint">
                            <strong>GET /api/hello</strong> - Simple text response
                        </div>
                        <div class="endpoint">
                            <strong>GET /api/json</strong> - JSON response
                        </div>
                        <div class="endpoint">
                            <strong>GET /api/info</strong> - Server information
                        </div>
                        
                        <h3>Try with cURL:</h3>
                        <pre><code>curl http://localhost:8080/api/hello
curl http://localhost:8080/api/json
curl http://localhost:8080/api/info</code></pre>
                    </body>
                    </html>
                    """);
            });

            // Simple text response
            server.routing().get("/api/hello", (req, res) -> {
                res.send("Hello from NovaServer v3.0! 👋");
            });

            // JSON response
            server.routing().get("/api/json", (req, res) -> {
                res.json("""
                    {
                        "message": "Hello, World!",
                        "server": "NovaServer",
                        "version": "3.0.0",
                        "timestamp": %d
                    }
                    """.formatted(System.currentTimeMillis()));
            });

            // Server info endpoint
            server.routing().get("/api/info", (req, res) -> {
                Runtime runtime = Runtime.getRuntime();
                long freeMemory = runtime.freeMemory() / (1024 * 1024);
                long totalMemory = runtime.totalMemory() / (1024 * 1024);
                long maxMemory = runtime.maxMemory() / (1024 * 1024);

                res.json("""
                    {
                        "server": "NovaServer",
                        "version": "3.0.0",
                        "port": %d,
                        "plugins": %d,
                        "java_version": "%s",
                        "memory": {
                            "free_mb": %d,
                            "total_mb": %d,
                            "max_mb": %d
                        }
                    }
                    """.formatted(
                    server.getConfig().port(),
                    server.getPluginManager().pluginCount(),
                    System.getProperty("java.version"),
                    freeMemory,
                    totalMemory,
                    maxMemory
                ));
            });

            // Health check endpoint
            server.routing().get("/health", (req, res) -> {
                res.status(HTTPStatus.OK)
                        .json("{\"status\": \"healthy\", \"uptime_ms\": " + 
                              (System.currentTimeMillis()) + "}");
            });

            // Start server
            server.start();

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   NovaServer v3.0 - Hello World        ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  Server: http://localhost:8080         ║");
            System.out.println("║  Status: Running ✓                     ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println();
            System.out.println("📝 Endpoints:");
            System.out.println("   • http://localhost:8080/");
            System.out.println("   • http://localhost:8080/api/hello");
            System.out.println("   • http://localhost:8080/api/json");
            System.out.println("   • http://localhost:8080/api/info");
            System.out.println("   • http://localhost:8080/health");
            System.out.println();
            System.out.println("Press Ctrl+C to stop...");

            // Wait for server
            server.await();

        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
