package com.lostfound.handler;

import com.lostfound.config.DBConnection;
import com.lostfound.util.CorsUtil;
import com.lostfound.util.ResponseUtil;
import com.mongodb.client.MongoCollection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;

public class NotificationHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsUtil.handle(exchange)) return;
        try {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET" -> list(exchange);
                case "PUT" -> markRead(exchange);
                default -> ResponseUtil.json(exchange, 405, ResponseUtil.message("Method not allowed"));
            }
        } catch (IllegalArgumentException e) {
            ResponseUtil.json(exchange, 400, ResponseUtil.message(e.getMessage()));
        } catch (Exception e) {
            ResponseUtil.json(exchange, 500, ResponseUtil.message("Server error: " + e.getMessage()));
        }
    }

    private void list(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String userId = queryValue(query, "userId");
        boolean unreadOnly = "true".equalsIgnoreCase(queryValue(query, "unreadOnly"));
        if (userId.isBlank()) throw new IllegalArgumentException("User id is required");

        MongoCollection<Document> collection = DBConnection.getDatabase().getCollection("notifications");
        List<Map<String, Object>> result = new ArrayList<>();
        var filter = unreadOnly ? and(eq("userId", userId), eq("isRead", false)) : eq("userId", userId);
        for (Document doc : collection.find(filter).sort(descending("createdAt")).limit(100)) {
            result.add(ResponseUtil.documentToMap(doc));
        }
        ResponseUtil.json(exchange, 200, result);
    }

    private void markRead(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String userId = queryValue(query, "userId");
        String id = queryValue(query, "id");
        String action = queryValue(query, "action");
        if (userId.isBlank()) throw new IllegalArgumentException("User id is required");

        MongoCollection<Document> collection = DBConnection.getDatabase().getCollection("notifications");
        if ("all".equalsIgnoreCase(action)) {
            collection.updateMany(and(eq("userId", userId), eq("isRead", false)),
                    new Document("$set", new Document("isRead", true)));
        } else {
            if (id.isBlank()) throw new IllegalArgumentException("Notification id is required");
            collection.updateOne(and(eq("_id", new ObjectId(id)), eq("userId", userId)),
                    new Document("$set", new Document("isRead", true)));
        }
        ResponseUtil.json(exchange, 200, ResponseUtil.message("Notification updated"));
    }

    private String queryValue(String query, String key) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return pair[1];
        }
        return "";
    }
}
