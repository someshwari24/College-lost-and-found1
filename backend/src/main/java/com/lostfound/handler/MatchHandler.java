package com.lostfound.handler;

import com.lostfound.algorithm.MatchingAlgorithm;
import com.lostfound.config.DBConnection;
import com.lostfound.util.CorsUtil;
import com.lostfound.util.ResponseUtil;
import com.mongodb.client.MongoCollection;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

public class MatchHandler implements HttpHandler {

    private static final double MINIMUM_MATCH_SCORE = 50.0;

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (CorsUtil.handle(exchange)) {
            return;
        }

        try {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                ResponseUtil.json(
                        exchange,
                        405,
                        ResponseUtil.message("Method not allowed")
                );
                return;
            }

            listMatches(exchange);

        } catch (IllegalArgumentException exception) {

            ResponseUtil.json(
                    exchange,
                    400,
                    ResponseUtil.message(exception.getMessage())
            );

        } catch (Exception exception) {

            exception.printStackTrace();

            ResponseUtil.json(
                    exchange,
                    500,
                    ResponseUtil.message(
                            "Server error: " + exception.getMessage()
                    )
            );
        }
    }

    private void listMatches(HttpExchange exchange)
            throws IOException {

        String query = exchange.getRequestURI().getQuery();

        String itemId = queryValue(
                query,
                "itemId"
        );

        String userId = queryValue(
                query,
                "userId"
        );

        if (itemId.isBlank()) {
            throw new IllegalArgumentException(
                    "Item id is required"
            );
        }

        ObjectId sourceObjectId = parseObjectId(
                itemId,
                "Invalid item id"
        );

        MongoCollection<Document> items =
                DBConnection
                        .getDatabase()
                        .getCollection("items");

        Document sourceItem = items.find(
                eq("_id", sourceObjectId)
        ).first();

        if (sourceItem == null) {
            throw new IllegalArgumentException(
                    "Item not found"
            );
        }

        /*
         * When userId is provided, ensure that the student owns
         * the selected report.
         */
        if (
                !userId.isBlank()
                        && !userId.equals(
                                sourceItem.getString("userId")
                )
        ) {
            throw new IllegalArgumentException(
                    "You can view matches only for your own report"
            );
        }

        String sourceType =
                safeString(sourceItem.get("type"))
                        .toUpperCase();

        if (
                !"LOST".equals(sourceType)
                        && !"FOUND".equals(sourceType)
        ) {
            throw new IllegalArgumentException(
                    "Invalid source item type"
            );
        }

        String sourceCategory =
                safeString(sourceItem.get("category"));

        if (sourceCategory.isBlank()) {
            throw new IllegalArgumentException(
                    "Source item category is missing"
            );
        }

        String oppositeType =
                "LOST".equals(sourceType)
                        ? "FOUND"
                        : "LOST";

        List<Map<String, Object>> matches =
                new ArrayList<>();

        for (
                Document candidate :
                items.find(
                        and(
                                eq("type", oppositeType),
                                eq("category", sourceCategory),
                                in(
                                        "status",
                                        List.of(
                                                "ACTIVE",
                                                "MATCHED",
                                                "CLAIM_REQUESTED"
                                        )
                                )
                        )
                )
        ) {

            /*
             * Avoid matching reports created by the same user.
             */
            if (
                    sourceItem.getString("userId") != null
                            && sourceItem.getString("userId")
                            .equals(candidate.getString("userId"))
            ) {
                continue;
            }

            double score =
                    MatchingAlgorithm.calculateScore(
                            sourceItem,
                            candidate
                    );

            if (score < MINIMUM_MATCH_SCORE) {
                continue;
            }

            Map<String, Object> match =
                    ResponseUtil.documentToMap(candidate);

            match.put(
                    "score",
                    Math.round(score * 100.0) / 100.0
            );

            match.put(
                    "matchLevel",
                    MatchingAlgorithm.level(score)
            );

            match.put(
                    "sourceItemId",
                    sourceItem.getObjectId("_id").toHexString()
            );

            match.put(
                    "sourceType",
                    sourceType
            );

            match.put(
                    "sourceUserId",
                    sourceItem.getString("userId")
            );

            /*
             * Contact details are intentionally not returned here.
             * They will be returned by ClaimHandler only after
             * the finder approves the claim.
             */
            match.put("contactVisible", false);

            matches.add(match);
        }

        matches.sort(
                Comparator.comparingDouble(
                        match ->
                                -((Number) match.get("score"))
                                        .doubleValue()
                )
        );

        ResponseUtil.json(
                exchange,
                200,
                matches
        );
    }

    private ObjectId parseObjectId(
            String value,
            String message
    ) {

        if (
                value == null
                        || !ObjectId.isValid(value)
        ) {
            throw new IllegalArgumentException(message);
        }

        return new ObjectId(value);
    }

    private String safeString(Object value) {

        return value == null
                ? ""
                : value.toString().trim();
    }

    private String queryValue(
            String query,
            String key
    ) {

        if (query == null || query.isBlank()) {
            return "";
        }

        for (String part : query.split("&")) {

            String[] pair = part.split("=", 2);

            if (pair.length != 2) {
                continue;
            }

            String decodedKey = URLDecoder.decode(
                    pair[0],
                    StandardCharsets.UTF_8
            );

            if (decodedKey.equals(key)) {
                return URLDecoder.decode(
                        pair[1],
                        StandardCharsets.UTF_8
                );
            }
        }

        return "";
    }
}