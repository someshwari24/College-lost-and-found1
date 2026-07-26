package com.lostfound;

import com.lostfound.config.DBConnection;
import com.lostfound.handler.ClaimHandler;
import com.lostfound.handler.ItemHandler;
import com.lostfound.handler.MatchHandler;
import com.lostfound.handler.NotificationHandler;
import com.lostfound.handler.UploadHandler;
import com.lostfound.handler.UserHandler;
import com.sun.net.httpserver.HttpServer;
import java.nio.file.Path;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MainServer {

    public static void main(String[] args) {
        try {
            int port = Integer.parseInt(
                    System.getenv().getOrDefault("PORT", "8080")
            );

            DBConnection.getDatabase();

            HttpServer server = HttpServer.create(
                    new InetSocketAddress("0.0.0.0", port),
                    0
            );

            server.createContext(
                    "/api/register",
                    new UserHandler("register")
            );

            server.createContext(
                    "/api/login",
                    new UserHandler("login")
            );

            server.createContext(
                    "/api/items",
                    new ItemHandler()
            );

            server.createContext(
                    "/api/matches",
                    new MatchHandler()
            );

            server.createContext(
                    "/api/claims",
                    new ClaimHandler()
            );

            server.createContext(
                    "/api/notifications",
                    new NotificationHandler()
            );

            Path uploadRoot = Path.of(
        System.getenv().getOrDefault(
                "UPLOAD_DIR",
                "uploads/item-images"
        )
);

server.createContext(
        "/uploads",
        new UploadHandler(uploadRoot)
);
            server.setExecutor(
                    Executors.newFixedThreadPool(10)
            );

            server.start();

            System.out.println(
                    "Backend started on port " + port
            );

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}