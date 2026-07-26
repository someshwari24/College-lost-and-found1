package com.lostfound.handler;

import com.lostfound.config.DBConnection;
import com.lostfound.util.*;
import com.mongodb.client.MongoCollection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

public class UserHandler implements HttpHandler {
    private final String action;
    public UserHandler(String action) { this.action = action; }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsUtil.handle(exchange)) return;
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            ResponseUtil.json(exchange, 405, ResponseUtil.message("Method not allowed"));
            return;
        }
        try {
            if ("register".equals(action)) register(exchange); else login(exchange);
        } catch (Exception e) {
            ResponseUtil.json(exchange, 500, ResponseUtil.message("Server error: " + e.getMessage()));
        }
    }

    private void register(HttpExchange exchange) throws IOException {
        Map<String, Object> body = RequestUtil.readJson(exchange);
        String name = value(body, "name");
        String collegeId = value(body, "collegeId");
        String email = value(body, "email").toLowerCase();
        String phone = value(body, "phone");
        String password = value(body, "password");

        if (name.isBlank() || collegeId.isBlank() || email.isBlank() || password.length() < 6) {
            ResponseUtil.json(exchange, 400, ResponseUtil.message("Enter valid details. Password must have at least 6 characters."));
            return;
        }
        if (phone.isBlank() || !phone.matches("[0-9+\\-\\s]{7,15}")) {
            ResponseUtil.json(exchange, 400, ResponseUtil.message("Enter a valid phone number (7-15 digits)."));
            return;
        }

        MongoCollection<Document> users = DBConnection.getDatabase().getCollection("users");
        if (users.find(eq("email", email)).first() != null) {
            ResponseUtil.json(exchange, 409, ResponseUtil.message("Email already registered"));
            return;
        }

        Document user = new Document("name", name)
                .append("collegeId", collegeId)
                .append("email", email)
                .append("phone", phone)
                .append("passwordHash", PasswordUtil.hash(password))
                .append("role", "STUDENT")
                .append("createdAt", Instant.now().toString());
        users.insertOne(user);
        ResponseUtil.json(exchange, 201, ResponseUtil.message("Registration successful"));
    }

    private void login(HttpExchange exchange) throws IOException {
        Map<String, Object> body = RequestUtil.readJson(exchange);
        String email = value(body, "email").toLowerCase();
        String password = value(body, "password");

        Document user = DBConnection.getDatabase().getCollection("users").find(eq("email", email)).first();
        if (user == null || !PasswordUtil.verify(password, user.getString("passwordHash"))) {
            ResponseUtil.json(exchange, 401, ResponseUtil.message("Invalid email or password"));
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Login successful");
        result.put("userId", user.getObjectId("_id").toHexString());
        result.put("name", user.getString("name"));
        result.put("email", user.getString("email"));
        result.put("phone", user.getString("phone"));
        result.put("role", user.getString("role"));
        ResponseUtil.json(exchange, 200, result);
    }

    private String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString().trim();
    }
}
