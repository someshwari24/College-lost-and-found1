package com.lostfound.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ResponseUtil {
    private static final Gson GSON = new GsonBuilder().create();
    private ResponseUtil() {}

    public static void json(HttpExchange exchange, int status, Object body) throws IOException {
        String json = GSON.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    public static Map<String, Object> message(String message) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("message", message);
        return map;
    }

    public static Map<String, Object> documentToMap(Document document) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : document.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ObjectId id) value = id.toHexString();
            map.put(entry.getKey(), value);
        }
        return map;
    }
}
