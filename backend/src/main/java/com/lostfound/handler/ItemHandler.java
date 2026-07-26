package com.lostfound.handler;

import com.lostfound.algorithm.MatchingAlgorithm;
import com.lostfound.config.DBConnection;
import com.lostfound.util.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.DeleteResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;


import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;

public class ItemHandler implements HttpHandler {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsUtil.handle(exchange)) return;
        try {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "POST" -> add(exchange);
                case "GET" -> list(exchange);
                case "PUT" -> resolve(exchange);
                case "DELETE" -> delete(exchange);
                default -> ResponseUtil.json(exchange, 405, ResponseUtil.message("Method not allowed"));
            }
        } catch (IllegalArgumentException e) {
            ResponseUtil.json(exchange, 400, ResponseUtil.message(e.getMessage()));
        } catch (Exception e) {
            ResponseUtil.json(exchange, 500, ResponseUtil.message("Server error: " + e.getMessage()));
        }
    }

    private void add(HttpExchange exchange) throws IOException {
        MultipartUtil.MultipartData multipart = MultipartUtil.parse(exchange);
        Map<String, String> body = multipart.fields();
        String type = value(body, "type").toUpperCase();
        if (!type.equals("LOST") && !type.equals("FOUND")) {
            throw new IllegalArgumentException("Type must be LOST or FOUND");
        }
        if (value(body, "userId").isBlank() || value(body, "itemName").isBlank() || value(body, "category").isBlank()) {
            throw new IllegalArgumentException("User, item name and category are required");
        }

        String imageUrl = "";
        MultipartUtil.FilePart image = multipart.files().get("image");
        if (image != null && image.data().length > 0) {
            imageUrl = saveImage(exchange, image);
        }

        Document item = new Document("userId", value(body, "userId"))
                .append("type", type)
                .append("itemName", value(body, "itemName"))
                .append("category", value(body, "category"))
                .append("color", value(body, "color"))
                .append("brand", value(body, "brand"))
                .append("description", value(body, "description"))
                .append("location", value(body, "location"))
                .append("eventDate", value(body, "eventDate"))
                .append("imageUrl", imageUrl)
                .append("status", "ACTIVE")
                .append("createdAt", Instant.now().toString());

        MongoCollection<Document> items = DBConnection.getDatabase().getCollection("items");
        items.insertOne(item);
        createMatchNotifications(items, item);
        ResponseUtil.json(exchange, 201, ResponseUtil.message(type + " item reported successfully"));
    }

    private String saveImage(
            HttpExchange exchange,
            MultipartUtil.FilePart image
    ) throws IOException {

        if (image.data().length > MAX_IMAGE_SIZE) {
            throw new IllegalArgumentException(
                    "Image must be 10 MB or smaller"
            );
        }

        if (
                image.contentType() == null ||
                !image.contentType()
                        .toLowerCase()
                        .startsWith("image/")
        ) {
            throw new IllegalArgumentException(
                    "Please select a valid image file"
            );
        }

        String extension =
                safeExtension(image.filename());

        String storedName =
                UUID.randomUUID() + extension;

        Path uploadRoot = Path.of(
                System.getenv().getOrDefault(
                        "UPLOAD_DIR",
                        "uploads/item-images"
                )
        ).toAbsolutePath().normalize();

        Files.createDirectories(uploadRoot);

        Path savedFile =
                uploadRoot.resolve(storedName)
                        .normalize();

        if (!savedFile.startsWith(uploadRoot)) {
            throw new IOException(
                    "Invalid image storage path"
            );
        }

        Files.write(
                savedFile,
                image.data()
        );

        String host =
                exchange.getRequestHeaders()
                        .getFirst("Host");

        if (host == null || host.isBlank()) {
            host = "localhost:8080";
        }

        String forwardedProto =
                exchange.getRequestHeaders()
                        .getFirst("X-Forwarded-Proto");

        String scheme =
                forwardedProto == null ||
                forwardedProto.isBlank()
                        ? "http"
                        : forwardedProto;

        return scheme +
                "://" +
                host +
                "/uploads/" +
                storedName;
    }

    private String safeExtension(String filename) {
        if (filename == null) return ".img";
        String cleaned = Path.of(filename).getFileName().toString();
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) return ".img";
        String ext = cleaned.substring(dot).toLowerCase();
        return ext.matches("\\.[a-z0-9]{1,8}") ? ext : ".img";
    }

    private void createMatchNotifications(MongoCollection<Document> items, Document source) {
        String oppositeType = "LOST".equals(source.getString("type")) ? "FOUND" : "LOST";
        int matchCount = 0;
        for (Document candidate : items.find(and(
                eq("type", oppositeType),
                ne("status", "RESOLVED"),
                eq("category", source.getString("category"))
        ))) {
            double score = MatchingAlgorithm.calculateScore(source, candidate);
            if (score < 70) continue;
            matchCount++;
            NotificationUtil.create(candidate.getString("userId"), "POSSIBLE_MATCH",
                    "A new " + source.getString("type").toLowerCase() + " report may match your " +
                            candidate.getString("itemName") + " (" + Math.round(score) + "%).",
                    source.getObjectId("_id").toHexString());
        }
        if (matchCount > 0) {
            NotificationUtil.create(source.getString("userId"), "POSSIBLE_MATCH",
                    matchCount + " possible match" + (matchCount == 1 ? " was" : "es were") +
                            " found for your " + source.getString("itemName") + ".",
                    source.getObjectId("_id").toHexString());
        }
    }

    private void list(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String userId = queryValue(query, "userId");
        MongoCollection<Document> items = DBConnection.getDatabase().getCollection("items");
        List<Map<String, Object>> result = new ArrayList<>();
        var iterable = userId.isBlank()
                ? items.find(ne("status", "RESOLVED")).sort(descending("createdAt"))
                : items.find(eq("userId", userId)).sort(descending("createdAt"));
        for (Document doc : iterable) result.add(ResponseUtil.documentToMap(doc));
        ResponseUtil.json(exchange, 200, result);
    }

    private void resolve(HttpExchange exchange) throws IOException {
        String id = queryValue(exchange.getRequestURI().getQuery(), "id");
        String userId = queryValue(exchange.getRequestURI().getQuery(), "userId");
        if (id.isBlank() || userId.isBlank()) throw new IllegalArgumentException("Item id and user id are required");
        Document item = ownedItem(id, userId);
        if ("RESOLVED".equals(item.getString("status"))) throw new IllegalArgumentException("Item is already resolved");
        DBConnection.getDatabase().getCollection("items")
                .updateOne(eq("_id", item.getObjectId("_id")), new Document("$set", new Document("status", "RESOLVED")));
        ResponseUtil.json(exchange, 200, ResponseUtil.message("Item marked as resolved"));
    }

    private void delete(HttpExchange exchange) throws IOException {
        String id = queryValue(exchange.getRequestURI().getQuery(), "id");
        String userId = queryValue(exchange.getRequestURI().getQuery(), "userId");
        if (id.isBlank() || userId.isBlank()) throw new IllegalArgumentException("Item id and user id are required");
        Document item = ownedItem(id, userId);

        MongoCollection<Document> claims = DBConnection.getDatabase().getCollection("claims");
        Document activeClaim = claims.find(and(
                or(eq("lostItemId", id), eq("foundItemId", id)),
                nin("status", "REJECTED", "RESOLVED")
        )).first();
        if (activeClaim != null) {
            throw new IllegalArgumentException("This item has an active claim and cannot be deleted");
        }

        DeleteResult result = DBConnection.getDatabase().getCollection("items")
                .deleteOne(and(eq("_id", item.getObjectId("_id")), eq("userId", userId)));
        if (result.getDeletedCount() == 0) throw new IllegalArgumentException("Item could not be deleted");
        deleteStoredImage(item.getString("imageUrl"));
        ResponseUtil.json(exchange, 200, ResponseUtil.message("Item deleted successfully"));
    }

    private Document ownedItem(String id, String userId) {
        ObjectId objectId;
        try { objectId = new ObjectId(id); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid item id"); }
        Document item = DBConnection.getDatabase().getCollection("items")
                .find(and(eq("_id", objectId), eq("userId", userId))).first();
        if (item == null) throw new IllegalArgumentException("Item not found or you are not allowed to modify it");
        return item;
    }

    private void deleteStoredImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        int marker = imageUrl.indexOf("/uploads/");
        if (marker < 0) return;
        String filename = imageUrl.substring(marker + "/uploads/".length());
        if (filename.contains("/") || filename.contains("\\")) return;
        Path uploadRoot = Path.of(System.getenv().getOrDefault("UPLOAD_DIR", "uploads/item-images"));
        try { Files.deleteIfExists(uploadRoot.resolve(filename)); } catch (IOException ignored) {}
    }

    private String value(Map<String, String> map, String key) {
        String value = map.get(key);
        return value == null ? "" : value.trim();
    }

    private String queryValue(String query, String key) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) return java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8);
        }
        return "";
    }
}