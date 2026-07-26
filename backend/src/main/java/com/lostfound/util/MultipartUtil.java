package com.lostfound.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MultipartUtil {
    private static final int MAX_REQUEST_SIZE = 12 * 1024 * 1024;

    private MultipartUtil() {}

    public record FilePart(String filename, String contentType, byte[] data) {}
    public record MultipartData(Map<String, String> fields, Map<String, FilePart> files) {}

    public static MultipartData parse(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/form-data")) {
            throw new IllegalArgumentException("Request must use multipart/form-data");
        }

        String boundary = extractBoundary(contentType);
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_SIZE + 1);
        if (body.length > MAX_REQUEST_SIZE) {
            throw new IllegalArgumentException("Upload is too large. Maximum request size is 12 MB");
        }

        String raw = new String(body, StandardCharsets.ISO_8859_1);
        String delimiter = "--" + boundary;
        Map<String, String> fields = new HashMap<>();
        Map<String, FilePart> files = new HashMap<>();

        for (String section : raw.split(java.util.regex.Pattern.quote(delimiter))) {
            if (section.isBlank() || section.equals("--\r\n") || section.equals("--")) continue;
            if (section.startsWith("\r\n")) section = section.substring(2);
            if (section.endsWith("\r\n")) section = section.substring(0, section.length() - 2);
            if (section.endsWith("--")) section = section.substring(0, section.length() - 2);

            int headerEnd = section.indexOf("\r\n\r\n");
            if (headerEnd < 0) continue;

            String headers = section.substring(0, headerEnd);
            String content = section.substring(headerEnd + 4);
            if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);

            String disposition = headerValue(headers, "Content-Disposition");
            String name = dispositionParameter(disposition, "name");
            if (name == null || name.isBlank()) continue;

            String filename = dispositionParameter(disposition, "filename");
            if (filename != null && !filename.isBlank()) {
                String fileContentType = headerValue(headers, "Content-Type");
                byte[] fileBytes = content.getBytes(StandardCharsets.ISO_8859_1);
                files.put(name, new FilePart(filename, fileContentType == null ? "application/octet-stream" : fileContentType, fileBytes));
            } else {
                fields.put(name, content);
            }
        }

        return new MultipartData(fields, files);
    }

    private static String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                String boundary = trimmed.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        throw new IllegalArgumentException("Multipart boundary is missing");
    }

    private static String headerValue(String headers, String headerName) {
        for (String line : headers.split("\r\n")) {
            int colon = line.indexOf(':');
            if (colon > 0 && line.substring(0, colon).trim().equalsIgnoreCase(headerName)) {
                return line.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static String dispositionParameter(String disposition, String key) {
        if (disposition == null) return null;
        for (String part : disposition.split(";")) {
            String trimmed = part.trim();
            String prefix = key + "=";
            if (trimmed.startsWith(prefix)) {
                String value = trimmed.substring(prefix.length());
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }
}
