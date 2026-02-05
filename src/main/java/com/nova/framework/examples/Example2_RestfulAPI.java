package com.nova.framework.examples;

import com.nova.framework.NovaServer;
import com.nova.framework.http.HTTPStatus;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Example 2: RESTful API
 * 
 * Demonstrates:
 * - RESTful CRUD operations
 * - Path parameters
 * - Query parameters
 * - In-memory data storage
 * - Proper HTTP status codes
 */
public class Example2_RestfulAPI {

    // In-memory user storage
    private static final Map<Long, User> users = new ConcurrentHashMap<>();
    private static final AtomicLong idCounter = new AtomicLong(1);

    public static void main(String[] args) {
        try {
            NovaServer server = new NovaServer(8080);

            // Add some initial data
            initializeData();

            // List all users (GET /api/users)
            server.routing().get("/api/users", (req, res) -> {
                String search = req.getQueryParam("search");
                
                if (search != null && !search.isEmpty()) {
                    // Filter users by name
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    for (User user : users.values()) {
                        if (user.name.toLowerCase().contains(search.toLowerCase())) {
                            if (!first) json.append(",");
                            json.append(user.toJson());
                            first = false;
                        }
                    }
                    json.append("]");
                    res.json(json.toString());
                } else {
                    // Return all users
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    for (User user : users.values()) {
                        if (!first) json.append(",");
                        json.append(user.toJson());
                        first = false;
                    }
                    json.append("]");
                    res.json(json.toString());
                }
            });

            // Get user by ID (GET /api/users/:id)
            server.routing().get("/api/users/:id", (req, res) -> {
                try {
                    long id = Long.parseLong(req.getPathParam("id"));
                    User user = users.get(id);

                    if (user == null) {
                        res.status(HTTPStatus.NOT_FOUND)
                                .json("{\"error\": \"User not found\", \"id\": " + id + "}");
                    } else {
                        res.json(user.toJson());
                    }
                } catch (NumberFormatException e) {
                    res.status(HTTPStatus.BAD_REQUEST)
                            .json("{\"error\": \"Invalid user ID\"}");
                }
            });

            // Create user (POST /api/users)
            server.routing().post("/api/users", (req, res) -> {
                try {
                    // Simple JSON parsing (in production use a proper JSON library)
                    String body = req.body();
                    String name = extractJsonField(body, "name");
                    String email = extractJsonField(body, "email");

                    if (name == null || email == null) {
                        res.status(HTTPStatus.BAD_REQUEST)
                                .json("{\"error\": \"Missing required fields: name, email\"}");
                        return;
                    }

                    long id = idCounter.getAndIncrement();
                    User user = new User(id, name, email);
                    users.put(id, user);

                    res.status(HTTPStatus.CREATED)
                            .json(user.toJson());
                } catch (Exception e) {
                    res.status(HTTPStatus.BAD_REQUEST)
                            .json("{\"error\": \"Invalid JSON\"}");
                }
            });

            // Update user (PUT /api/users/:id)
            server.routing().put("/api/users/:id", (req, res) -> {
                try {
                    long id = Long.parseLong(req.getPathParam("id"));
                    User existing = users.get(id);

                    if (existing == null) {
                        res.status(HTTPStatus.NOT_FOUND)
                                .json("{\"error\": \"User not found\"}");
                        return;
                    }

                    String body = req.body();
                    String name = extractJsonField(body, "name");
                    String email = extractJsonField(body, "email");

                    if (name != null) existing.name = name;
                    if (email != null) existing.email = email;

                    res.json(existing.toJson());
                } catch (NumberFormatException e) {
                    res.status(HTTPStatus.BAD_REQUEST)
                            .json("{\"error\": \"Invalid user ID\"}");
                }
            });

            // Delete user (DELETE /api/users/:id)
            server.routing().delete("/api/users/:id", (req, res) -> {
                try {
                    long id = Long.parseLong(req.getPathParam("id"));
                    User removed = users.remove(id);

                    if (removed == null) {
                        res.status(HTTPStatus.NOT_FOUND)
                                .json("{\"error\": \"User not found\"}");
                    } else {
                        res.status(HTTPStatus.NO_CONTENT).send("");
                    }
                } catch (NumberFormatException e) {
                    res.status(HTTPStatus.BAD_REQUEST)
                            .json("{\"error\": \"Invalid user ID\"}");
                }
            });

            // API documentation
            server.routing().get("/", (req, res) -> {
                res.html("""
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <title>RESTful API - NovaServer</title>
                        <style>
                            body { font-family: Arial; max-width: 900px; margin: 50px auto; padding: 20px; }
                            .endpoint { background: #f4f4f4; padding: 15px; margin: 15px 0; border-radius: 5px; }
                            .method { display: inline-block; padding: 3px 8px; border-radius: 3px; font-weight: bold; color: white; }
                            .get { background: #61affe; }
                            .post { background: #49cc90; }
                            .put { background: #fca130; }
                            .delete { background: #f93e3e; }
                            code { background: #e0e0e0; padding: 2px 5px; }
                        </style>
                    </head>
                    <body>
                        <h1>📚 RESTful API Documentation</h1>
                        
                        <div class="endpoint">
                            <span class="method get">GET</span> <strong>/api/users</strong>
                            <p>Get all users. Optional query param: <code>?search=name</code></p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method get">GET</span> <strong>/api/users/:id</strong>
                            <p>Get user by ID</p>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method post">POST</span> <strong>/api/users</strong>
                            <p>Create new user</p>
                            <pre><code>{ "name": "John Doe", "email": "john@example.com" }</code></pre>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method put">PUT</span> <strong>/api/users/:id</strong>
                            <p>Update user</p>
                            <pre><code>{ "name": "New Name", "email": "new@example.com" }</code></pre>
                        </div>
                        
                        <div class="endpoint">
                            <span class="method delete">DELETE</span> <strong>/api/users/:id</strong>
                            <p>Delete user</p>
                        </div>
                        
                        <h2>Quick Test:</h2>
                        <pre><code># List all users
curl http://localhost:8080/api/users

# Get specific user
curl http://localhost:8080/api/users/1

# Search users
curl http://localhost:8080/api/users?search=alice

# Create user
curl -X POST http://localhost:8080/api/users \\
  -H "Content-Type: application/json" \\
  -d '{"name":"Bob","email":"bob@example.com"}'

# Update user
curl -X PUT http://localhost:8080/api/users/1 \\
  -H "Content-Type: application/json" \\
  -d '{"name":"Alice Updated"}'

# Delete user
curl -X DELETE http://localhost:8080/api/users/1</code></pre>
                    </body>
                    </html>
                    """);
            });

            server.start();

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║   NovaServer - RESTful API             ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  API: http://localhost:8080/api/users  ║");
            System.out.println("║  Docs: http://localhost:8080/          ║");
            System.out.println("╚════════════════════════════════════════╝");
            System.out.println("\n✅ Server started with " + users.size() + " initial users");

            server.await();

        } catch (Exception e) {
            System.err.println("❌ Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void initializeData() {
        users.put(1L, new User(1L, "Alice Johnson", "alice@example.com"));
        users.put(2L, new User(2L, "Bob Smith", "bob@example.com"));
        users.put(3L, new User(3L, "Charlie Brown", "charlie@example.com"));
        idCounter.set(4L);
    }

    // Simple JSON field extractor (use a proper JSON library in production)
    private static String extractJsonField(String json, String field) {
        String pattern = "\"" + field + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    // User model
    static class User {
        long id;
        String name;
        String email;

        User(long id, String name, String email) {
            this.id = id;
            this.name = name;
            this.email = email;
        }

        String toJson() {
            return String.format(
                    "{\"id\": %d, \"name\": \"%s\", \"email\": \"%s\"}",
                    id, name, email
            );
        }
    }
}
