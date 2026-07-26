package com.lostfound.util;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class RequestUtil {
    private static final Gson GSON = new Gson();
    private RequestUtil() {}

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readJson(HttpExchange exchange) {
        return GSON.fromJson(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8), Map.class);
    }
}
