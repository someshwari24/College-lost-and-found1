package com.lostfound.handler;

import com.lostfound.util.CorsUtil;
import com.lostfound.util.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadHandler implements HttpHandler {
    private final Path uploadRoot;

    public UploadHandler(Path uploadRoot) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (CorsUtil.handle(exchange)) return;
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            ResponseUtil.json(exchange, 405, ResponseUtil.message("Method not allowed"));
            return;
        }

        String relative = exchange.getRequestURI().getPath().replaceFirst("^/uploads/?", "");
        Path file = uploadRoot.resolve(relative).normalize();
        if (!file.startsWith(uploadRoot) || !Files.isRegularFile(file)) {
            ResponseUtil.json(exchange, 404, ResponseUtil.message("Image not found"));
            return;
        }

        String contentType = Files.probeContentType(file);
        exchange.getResponseHeaders().set("Content-Type", contentType == null ? "application/octet-stream" : contentType);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        byte[] bytes = Files.readAllBytes(file);
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
