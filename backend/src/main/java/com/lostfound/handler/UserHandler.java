package com.lostfound.handler;

import com.lostfound.config.DBConnection;
import com.lostfound.util.CorsUtil;
import com.lostfound.util.PasswordUtil;
import com.lostfound.util.RequestUtil;
import com.lostfound.util.ResponseUtil;
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

    public UserHandler(String action) {
        this.action = action;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (CorsUtil.handle(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            ResponseUtil.json(
                    exchange,
                    405,
                    ResponseUtil.message("Method not allowed")
            );
            return;
        }

        try {
            if ("register".equalsIgnoreCase(action)) {
                register(exchange);
            } else if ("login".equalsIgnoreCase(action)) {
                login(exchange);
            } else {
                ResponseUtil.json(
                        exchange,
                        404,
                        ResponseUtil.message("Invalid action")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();

            ResponseUtil.json(
                    exchange,
                    500,
                    ResponseUtil.message(
                            "Server error: " + e.getMessage()
                    )
            );
        }
    }

    private void register(HttpExchange exchange) throws IOException {

        Map<String, Object> body =
                RequestUtil.readJson(exchange);

        String name =
                value(body, "name");

        String department =
                value(body, "department");

        String email =
                value(body, "email").toLowerCase();

        String phone =
                value(body, "phone");

        String password =
                rawValue(body, "password");

        System.out.println(
                "Registration request fields: " +
                body.keySet()
        );

        System.out.println(
                "Password length received: " +
                password.length()
        );

        if (name.isBlank()) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Full name is required."
                    )
            );
            return;
        }

        if (
                email.isBlank() ||
                !email.matches(
                        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
                )
        ) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Enter a valid email address."
                    )
            );
            return;
        }

        if (password.length() < 6) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Password must have at least 6 characters."
                    )
            );
            return;
        }

        if (department.isBlank()) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Department is required."
                    )
            );
            return;
        }

        if (!phone.matches("\\d{10}")) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Phone number must contain exactly 10 digits."
                    )
            );
            return;
        }

        MongoCollection<Document> users =
                DBConnection
                        .getDatabase()
                        .getCollection("users");

        if (users.find(eq("email", email)).first() != null) {
            ResponseUtil.json(
                    exchange,
                    409,
                    ResponseUtil.message(
                            "Email already registered."
                    )
            );
            return;
        }

        Document user =
                new Document("name", name)
                        .append("department", department)
                        .append("email", email)
                        .append("phone", phone)
                        .append(
                                "passwordHash",
                                PasswordUtil.hash(password)
                        )
                        .append("role", "STUDENT")
                        .append(
                                "createdAt",
                                Instant.now().toString()
                        );

        users.insertOne(user);

        ResponseUtil.json(
                exchange,
                201,
                ResponseUtil.message(
                        "Registration successful."
                )
        );
    }

    private void login(HttpExchange exchange)
            throws IOException {

        Map<String, Object> body =
                RequestUtil.readJson(exchange);

        String email =
                value(body, "email").toLowerCase();

        String password =
                rawValue(body, "password");

        if (email.isBlank() || password.isBlank()) {
            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(
                            "Email and password are required."
                    )
            );
            return;
        }

        MongoCollection<Document> users =
                DBConnection
                        .getDatabase()
                        .getCollection("users");

        Document user =
                users.find(eq("email", email)).first();

        if (
                user == null ||
                user.getString("passwordHash") == null ||
                !PasswordUtil.verify(
                        password,
                        user.getString("passwordHash")
                )
        ) {
            ResponseUtil.json(
                    exchange,
                    401,
                    ResponseUtil.message(
                            "Invalid email or password."
                    )
            );
            return;
        }

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put("message", "Login successful.");
        result.put(
                "userId",
                user.getObjectId("_id").toHexString()
        );
        result.put("name", user.getString("name"));
        result.put(
                "department",
                user.getString("department")
        );
        result.put("email", user.getString("email"));
        result.put("phone", user.getString("phone"));
        result.put("role", user.getString("role"));

        ResponseUtil.json(
                exchange,
                200,
                result
        );
    }

    private String value(
            Map<String, Object> map,
            String key
    ) {
        Object value = map.get(key);

        return value == null
                ? ""
                : value.toString().trim();
    }

    private String rawValue(
            Map<String, Object> map,
            String key
    ) {
        Object value = map.get(key);

        return value == null
                ? ""
                : value.toString();
    }
}