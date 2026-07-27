package com.lostfound.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class RequestUtil {

    private static final Gson GSON = new Gson();

    private RequestUtil() {}

    public static Map<String, Object> readJson(HttpExchange exchange)
            throws IOException {

        Type type = new TypeToken<Map<String, Object>>() {}.getType();

        try (InputStreamReader reader = new InputStreamReader(
                exchange.getRequestBody(),
                StandardCharsets.UTF_8
        )) {
            Map<String, Object> body = GSON.fromJson(reader, type);

            return body == null ? Map.of() : body;
        }
    }
}