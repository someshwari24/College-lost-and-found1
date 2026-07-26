package com.lostfound.handler;

import com.lostfound.util.CorsUtil;
import com.lostfound.util.ResponseUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadHandler implements HttpHandler {

    private final Path uploadRoot;

    public UploadHandler(Path uploadRoot) {
        this.uploadRoot =
                uploadRoot.toAbsolutePath().normalize();

        try {
            Files.createDirectories(this.uploadRoot);

            System.out.println(
                    "Upload folder initialized: " +
                    this.uploadRoot
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create upload directory",
                    exception
            );
        }
    }

    @Override
    public void handle(HttpExchange exchange)
            throws IOException {

        if (CorsUtil.handle(exchange)) {
            return;
        }

        if (
                !"GET".equalsIgnoreCase(
                        exchange.getRequestMethod()
                )
        ) {

            ResponseUtil.json(
                    exchange,
                    405,
                    ResponseUtil.message(
                            "Method not allowed"
                    )
            );

            return;
        }

        String requestPath =
                exchange.getRequestURI().getPath();

        String relativePath =
                requestPath.replaceFirst(
                        "^/uploads/?",
                        ""
                );

        if (relativePath.isBlank()) {

            ResponseUtil.json(
                    exchange,
                    404,
                    ResponseUtil.message(
                            "Image name is required"
                    )
            );

            return;
        }

        Path requestedFile =
                uploadRoot.resolve(relativePath)
                        .normalize();

        System.out.println(
                "Requested image URL: " +
                requestPath
        );

        System.out.println(
                "Upload root: " +
                uploadRoot
        );

        System.out.println(
                "Resolved file path: " +
                requestedFile
        );

        System.out.println(
                "File exists: " +
                Files.exists(requestedFile)
        );

        /*
         * Prevent directory traversal attacks.
         *
         * Example blocked path:
         * /uploads/../../secret.txt
         */
        if (!requestedFile.startsWith(uploadRoot)) {

            ResponseUtil.json(
                    exchange,
                    403,
                    ResponseUtil.message(
                            "Invalid image path"
                    )
            );

            return;
        }

        if (!Files.isRegularFile(requestedFile)) {

            ResponseUtil.json(
                    exchange,
                    404,
                    ResponseUtil.message(
                            "Image not found"
                    )
            );

            return;
        }

        String contentType =
                Files.probeContentType(requestedFile);

        if (
                contentType == null ||
                !contentType.startsWith("image/")
        ) {

            contentType =
                    getContentTypeFromFilename(
                            requestedFile.getFileName()
                                    .toString()
                    );
        }

        long fileSize =
                Files.size(requestedFile);

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "public, max-age=86400"
        );

        exchange.getResponseHeaders().set(
                "Content-Disposition",
                "inline"
        );

        exchange.sendResponseHeaders(
                200,
                fileSize
        );

        try (
                OutputStream outputStream =
                        exchange.getResponseBody()
        ) {

            Files.copy(
                    requestedFile,
                    outputStream
            );
        } finally {
            exchange.close();
        }
    }

    private String getContentTypeFromFilename(
            String filename
    ) {

        String lower =
                filename.toLowerCase();

        if (
                lower.endsWith(".jpg") ||
                lower.endsWith(".jpeg")
        ) {
            return "image/jpeg";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".gif")) {
            return "image/gif";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
        }

        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }

        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }
}