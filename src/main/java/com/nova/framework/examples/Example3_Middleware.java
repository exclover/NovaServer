package com.nova.framework.examples;

import com.nova.framework.NovaServer;
import com.nova.framework.http.HTTPStatus;
import com.nova.framework.plugins.MiddlewarePlugin;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Example 3: Middleware & Authentication
 * 
 * Demonstrates:
 * - Middleware for logging
 * - CORS middleware
 * - Authentication middleware
 * - Protected routes
 * - Public vs private endpoints
 */
public class Example3_Middleware {

    // Simple token storage (in production use proper auth system)
    private static final Set<String> validTokens = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) {
        try {
            NovaServer server = new NovaServer(8080);

            // Add some valid tokens for testing
            validTokens.add("secret-token-123");
            validTokens.add("admin-token-456");

            // ========== MIDDLEWARE SETUP ==========

            // 1. Logging Middleware (logs all requests)
            server.middleware().use((req, res) -> {
                long startTime = System.currentTimeMillis();
                System.out.printf("[%s] %s %s from %s%n",
                        java.time.LocalDateTime.now(),
                        req.method(),
                        req.path(),
                        req.clientAddress()
                );

                // Add timing header (this would need to be done after response)
                long duration = System.currentTimeMillis() - startTime;
                res.setHeader("X-Response-Time", duration + "ms");

                return MiddlewarePlugin.MiddlewareResult.CONTINUE;
            });

            // 2. CORS Middleware (enable cross-origin requests)
            server.middleware().use((req, res) -> {
                res.setHeader("Access-Control-Allow-Origin", "*");
                res.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");

                // Handle preflight OPTIONS request
                if (req.method().toString().equals("OPTIONS")) {
                    res.status(HTTPStatus.NO_CONTENT).send("");
                    return MiddlewarePlugin.MiddlewareResult.STOP;
                }

                return MiddlewarePlugin.MiddlewareResult.CONTINUE;
            });

            // 3. Authentication Middleware (for protected routes)
            server.middleware().use((req, res) -> {
                // Skip auth for public routes
                if (req.path().startsWith("/public") || req.path().equals("/") || req.path().equals("/login")) {
                    return MiddlewarePlugin.MiddlewareResult.CONTINUE;
                }

                // Check for protected routes
                if (req.path().startsWith("/api/protected")) {
                    String authHeader = req.getHeader("Authorization");

                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        res.status(HTTPStatus.UNAUTHORIZED)
                                .json("{\"error\": \"Missing or invalid authorization header\"}");
                        return MiddlewarePlugin.MiddlewareResult.STOP;
                    }

                    String token = authHeader.substring(7); // Remove "Bearer "

                    if (!validTokens.contains(token)) {
                        res.status(HTTPStatus.FORBIDDEN)
                                .json("{\"error\": \"Invalid token\"}");
                        return MiddlewarePlugin.MiddlewareResult.STOP;
                    }

                    // Token is valid, continue
                    System.out.println("✅ Authenticated request to " + req.path());
                }

                return MiddlewarePlugin.MiddlewareResult.CONTINUE;
            });

            // ========== ROUTES ==========

            // Home page
            server.routing().get("/", (req, res) -> {
                res.html("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>Middleware Example</title>
                        <style>
                            body { font-family: Arial; max-width: 900px; margin: 50px auto; padding: 20px; }
                            .section { background: #f9f9f9; padding: 20px; margin: 20px 0; border-left: 4px solid #4CAF50; }
                            code { background: #e0e0e0; padding: 2px 5px; border-radius: 3px; }
                            .public { border-left-color: #2196F3; }
                            .protected { border-left-color: #f44336; }
                        </style>
                    </head>
                    <body>
                        <h1>🔐 Middleware & Authentication Example</h1>
                        
                        <div class="section public">
                            <h2>Public Endpoints (No Auth Required)</h2>
                            <ul>
                                <li><code>GET /public/info</code> - Public information</li>
                                <li><code>POST /login</code> - Get authentication token</li>
                            </ul>
                        </div>
                        
                        <div class="section protected">
                            <h2>Protected Endpoints (Auth Required)</h2>
                            <p><strong>Requires:</strong> <code>Authorization: Bearer &lt;token&gt;</code> header</p>
                            <ul>
                                <li><code>GET /api/protected/profile</code> - User profile</li>
                                <li><code>GET /api/protected/dashboard</code> - User dashboard</li>
                                <li><code>GET /api/protected/admin</code> - Admin area</li>
                            </ul>
                        </div>
                        
                        <h2>Try It:</h2>
                        <pre><code># Get token
curl -X POST http://localhost:8080/login \\
  -H "Content-Type: application/json" \\
  -d '{"username":"admin","password":"admin"}'

# Access public endpoint (no auth needed)
curl http://localhost:8080/public/info

# Access protected endpoint (no auth - will fail)
curl http://localhost:8080/api/protected/profile

# Access protected endpoint (with auth - will succeed)
curl http://localhost:8080/api/protected/profile \\
  -H "Authorization: Bearer secret-token-123"

# Access admin endpoint
curl http://localhost:8080/api/protected/admin \\
  -H "Authorization: Bearer admin-token-456"</code></pre>
                    </body>
                    </html>
                    """);
            });

            // Public endpoint - no auth required
            server.routing().get("/public/info", (req, res) -> {
                res.json("""
                    {
                        "message": "This is a public endpoint",
                        "server": "NovaServer v3.0",
                        "auth_required": false,
                        "timestamp": %d
                    }
                    """.formatted(System.currentTimeMillis()));
            });

            // Login endpoint - returns token
            server.routing().post("/login", (req, res) -> {
                // In production: validate credentials against database
                String body = req.body();
                
                // Simple username check (not secure, just for demo)
                if (body.contains("admin")) {
                    res.json("""
                        {
                            "success": true,
                            "token": "secret-token-123",
                            "message": "Login successful"
                        }
                        """);
                } else {
                    res.status(HTTPStatus.UNAUTHORIZED)
                            .json("{\"success\": false, \"message\": \"Invalid credentials\"}");
                }
            });

            // Protected endpoint - profile
            server.routing().get("/api/protected/profile", (req, res) -> {
                String token = req.getHeader("Authorization").substring(7);
                res.json("""
                    {
                        "user": "authenticated_user",
                        "token": "%s",
                        "profile": {
                            "name": "John Doe",
                            "email": "john@example.com",
                            "role": "user"
                        }
                    }
                    """.formatted(token));
            });

            // Protected endpoint - dashboard
            server.routing().get("/api/protected/dashboard", (req, res) -> {
                res.json("""
                    {
                        "dashboard": {
                            "total_requests": 1234,
                            "active_users": 42,
                            "server_uptime_ms": %d
                        }
                    }
                    """.formatted(System.currentTimeMillis()));
            });

            // Protected endpoint - admin only
            server.routing().get("/api/protected/admin", (req, res) -> {
                String token = req.getHeader("Authorization").substring(7);
                
                if (token.startsWith("admin")) {
                    res.json("""
                        {
                            "admin_panel": true,
                            "users": ["alice", "bob", "charlie"],
                            "system_status": "healthy"
                        }
                        """);
                } else {
                    res.status(HTTPStatus.FORBIDDEN)
                            .json("{\"error\": \"Admin access required\"}");
                }
            });

            server.start();

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   NovaServer - Middleware Example      ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  Server: http://localhost:8080         ║");
            System.out.println("║  Middleware: 3 active                  ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println("\n🔑 Valid Tokens:");
            System.out.println("   • secret-token-123 (user)");
            System.out.println("   • admin-token-456 (admin)");
            System.out.println("\n📝 Middleware Chain:");
            System.out.println("   1. Request Logger");
            System.out.println("   2. CORS Handler");
            System.out.println("   3. Authentication");

            server.await();

        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
