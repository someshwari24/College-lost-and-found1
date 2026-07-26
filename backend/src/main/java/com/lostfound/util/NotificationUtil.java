package com.lostfound.util;

import com.lostfound.config.DBConnection;
import org.bson.Document;

import java.time.Instant;

public final class NotificationUtil {
    private NotificationUtil() {}

    public static void create(String userId, String type, String message, String relatedId) {
        if (userId == null || userId.isBlank()) return;
        Document notification = new Document("userId", userId)
                .append("type", type)
                .append("message", message)
                .append("relatedId", relatedId == null ? "" : relatedId)
                .append("isRead", false)
                .append("createdAt", Instant.now().toString());
        DBConnection.getDatabase().getCollection("notifications").insertOne(notification);
    }
}
