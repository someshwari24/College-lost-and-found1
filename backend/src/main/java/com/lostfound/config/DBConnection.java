package com.lostfound.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

public final class DBConnection {

    private static MongoClient client;
    private static MongoDatabase database;

    private DBConnection() {
    }

    public static synchronized MongoDatabase getDatabase() {

        if (database == null) {

            try {

                String uri = System.getenv("MONGODB_URI");
                String dbName = System.getenv("MONGODB_DB");

                // Local development fallback
                if (uri == null || uri.isBlank()) {
                    uri = "mongodb://localhost:27017";
                }

                if (dbName == null || dbName.isBlank()) {
                    dbName = "college_lost_found";
                }

                client = MongoClients.create(uri);

                database = client.getDatabase(dbName);

                // Test connection
                database.runCommand(new Document("ping", 1));

                System.out.println("=================================");
                System.out.println("MongoDB Connected Successfully");
                System.out.println("Database : " + dbName);
                System.out.println("=================================");

            } catch (Exception e) {

                System.err.println("=================================");
                System.err.println("MongoDB Connection Failed");
                System.err.println(e.getMessage());
                System.err.println("=================================");

                throw new RuntimeException("Unable to connect to MongoDB", e);
            }
        }

        return database;
    }

    public static synchronized void closeConnection() {

        if (client != null) {
            client.close();
            client = null;
            database = null;
            System.out.println("MongoDB Connection Closed");
        }
    }
}