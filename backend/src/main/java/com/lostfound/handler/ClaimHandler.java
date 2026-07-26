package com.lostfound.handler;

import com.lostfound.config.DBConnection;
import com.lostfound.util.CorsUtil;
import com.lostfound.util.NotificationUtil;
import com.lostfound.util.RequestUtil;
import com.lostfound.util.ResponseUtil;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;

public class ClaimHandler implements HttpHandler {

    private static final List<String> CONTACT_VISIBLE_STATUSES =
            List.of("APPROVED", "RETURNED", "RESOLVED");

    private static final List<String> ACTIVE_CLAIM_STATUSES =
            List.of("PENDING", "APPROVED", "RETURNED");

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        if (CorsUtil.handle(exchange)) {
            return;
        }

        try {

            switch (exchange.getRequestMethod().toUpperCase()) {

                case "POST" -> createClaim(exchange);

                case "GET" -> listClaims(exchange);

                case "PUT" -> updateClaim(exchange);

                default -> ResponseUtil.json(
                        exchange,
                        405,
                        ResponseUtil.message("Method not allowed")
                );
            }

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

    /*
     * POST /api/claims
     *
     * Expected JSON:
     *
     * {
     *   "lostItemId": "...",
     *   "foundItemId": "...",
     *   "claimantId": "...",
     *   "ownershipProof": "...",
     *   "additionalDetails": "...",
     *   "message": "..."
     * }
     */


    private void addItemImages(
        Map<String, Object> map,
        Document lostItem,
        Document foundItem
) {

    if (lostItem != null) {

        System.out.println(
                "Lost Item: " + lostItem.toJson()
        );

        String lostImage =
                safeString(
                        lostItem.get("imageUrl")
                );

        System.out.println(
                "Lost Image URL: " + lostImage
        );

        map.put(
                "lostItemImage",
                lostImage
        );

    } else {

        map.put(
                "lostItemImage",
                ""
        );
    }

    if (foundItem != null) {

        System.out.println(
                "Found Item: " + foundItem.toJson()
        );

        String foundImage =
                safeString(
                        foundItem.get("imageUrl")
                );

        System.out.println(
                "Found Image URL: " + foundImage
        );

        map.put(
                "foundItemImage",
                foundImage
        );

    } else {

        map.put(
                "foundItemImage",
                ""
        );
    }
}
    private void createClaim(HttpExchange exchange) throws IOException {

        Map<String, Object> body = RequestUtil.readJson(exchange);

        String lostItemId = value(body, "lostItemId");
        String foundItemId = value(body, "foundItemId");
        String claimantId = value(body, "claimantId");

        String ownershipProof = value(body, "ownershipProof");
        String additionalDetails = value(body, "additionalDetails");
        String message = value(body, "message");

        /*
         * Backward compatibility with the previous frontend.
         */
        if (ownershipProof.isBlank()) {
            ownershipProof = value(body, "verificationAnswer");
        }

        validateRequiredIds(
                lostItemId,
                foundItemId,
                claimantId
        );

        validateClaimText(
                ownershipProof,
                additionalDetails,
                message
        );

        ObjectId lostObjectId = parseObjectId(
                lostItemId,
                "Invalid lost item id"
        );

        ObjectId foundObjectId = parseObjectId(
                foundItemId,
                "Invalid found item id"
        );

        ObjectId claimantObjectId = parseObjectId(
                claimantId,
                "Invalid claimant id"
        );

        MongoCollection<Document> items =
                DBConnection
                        .getDatabase()
                        .getCollection("items");

        MongoCollection<Document> users =
                DBConnection
                        .getDatabase()
                        .getCollection("users");

        MongoCollection<Document> claims =
                DBConnection
                        .getDatabase()
                        .getCollection("claims");

        Document claimant = users.find(
                eq("_id", claimantObjectId)
        ).first();

        if (claimant == null) {
            throw new IllegalArgumentException(
                    "Claimant account was not found"
            );
        }

        Document lostItem = items.find(
                and(
                        eq("_id", lostObjectId),
                        eq("type", "LOST"),
                        in(
                                "status",
                                List.of(
                                        "ACTIVE",
                                        "MATCHED",
                                        "CLAIM_REQUESTED"
                                )
                        )
                )
        ).first();

        if (lostItem == null) {
            throw new IllegalArgumentException(
                    "Lost item is unavailable or already resolved"
            );
        }

        Document foundItem = items.find(
                and(
                        eq("_id", foundObjectId),
                        eq("type", "FOUND"),
                        in(
                                "status",
                                List.of(
                                        "ACTIVE",
                                        "MATCHED",
                                        "CLAIM_REQUESTED"
                                )
                        )
                )
        ).first();

        if (foundItem == null) {
            throw new IllegalArgumentException(
                    "Found item is unavailable or already resolved"
            );
        }

        String lostItemOwnerId =
                safeString(lostItem.get("userId"));

        String finderId =
                safeString(foundItem.get("userId"));

        if (lostItemOwnerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Lost item owner information is missing"
            );
        }

        if (finderId.isBlank()) {
            throw new IllegalArgumentException(
                    "Finder information is missing"
            );
        }

        /*
         * Only the student who created the lost post can claim the
         * corresponding found item.
         */
        if (!claimantId.equals(lostItemOwnerId)) {

            throw new IllegalArgumentException(
                    "You can claim only matches related to your own lost post"
            );
        }

        /*
         * Prevent a student from claiming their own found report.
         */
        if (claimantId.equals(finderId)) {

            throw new IllegalArgumentException(
                    "You cannot claim an item that you reported as found"
            );
        }

        /*
         * Basic category validation.
         */
        String lostCategory =
                normalize(lostItem.getString("category"));

        String foundCategory =
                normalize(foundItem.getString("category"));

        if (
                !lostCategory.isBlank()
                        && !foundCategory.isBlank()
                        && !lostCategory.equals(foundCategory)
        ) {

            throw new IllegalArgumentException(
                    "The selected lost and found items belong to different categories"
            );
        }

        /*
         * Prevent duplicate active claims.
         */
        Document duplicateClaim = claims.find(
                and(
                        eq("lostItemId", lostItemId),
                        eq("foundItemId", foundItemId),
                        eq("claimantId", claimantId),
                        in("status", ACTIVE_CLAIM_STATUSES)
                )
        ).first();

        if (duplicateClaim != null) {

            throw new IllegalArgumentException(
                    "You have already submitted a claim for this item"
            );
        }

        /*
         * Prevent another approved/returned claim for the same found item.
         */
        Document acceptedClaim = claims.find(
                and(
                        eq("foundItemId", foundItemId),
                        in(
                                "status",
                                List.of(
                                        "APPROVED",
                                        "RETURNED",
                                        "RESOLVED"
                                )
                        )
                )
        ).first();

        if (acceptedClaim != null) {

            throw new IllegalArgumentException(
                    "This found item has already been assigned to another claim"
            );
        }

        Document claim = new Document()
                .append("lostItemId", lostItemId)
                .append("foundItemId", foundItemId)
                .append("claimantId", claimantId)
                .append("finderId", finderId)
                .append("ownershipProof", ownershipProof)
                .append("additionalDetails", additionalDetails)
                .append("message", message)

                /*
                 * Retained for compatibility with your old claims page.
                 */
                .append("verificationAnswer", ownershipProof)

                .append("status", "PENDING")
                .append("finderReturned", false)
                .append("ownerReceived", false)
                .append("createdAt", Instant.now().toString())
                .append("updatedAt", Instant.now().toString());

        claims.insertOne(claim);

        String claimId =
                claim.getObjectId("_id").toHexString();

        NotificationUtil.create(
                finderId,
                "CLAIM_REQUEST",
                "A student submitted a claim for your found item: "
                        + safeItemName(foundItem),
                claimId
        );

        items.updateMany(
                in("_id", lostObjectId, foundObjectId),
                new Document(
                        "$set",
                        new Document()
                                .append(
                                        "status",
                                        "CLAIM_REQUESTED"
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        ResponseUtil.json(
                exchange,
                201,
                new Document()
                        .append(
                                "message",
                                "Claim request sent to the finder"
                        )
                        .append("claimId", claimId)
                        .append("status", "PENDING")
        );
    }

    /*
     * GET /api/claims?userId=...
     */
    private void listClaims(HttpExchange exchange)
            throws IOException {

        String userId = queryValue(
                exchange.getRequestURI().getQuery(),
                "userId"
        );

        if (userId.isBlank()) {
            throw new IllegalArgumentException(
                    "User id is required"
            );
        }

        ObjectId userObjectId = parseObjectId(
                userId,
                "Invalid user id"
        );

        MongoCollection<Document> users =
                DBConnection
                        .getDatabase()
                        .getCollection("users");

        if (users.find(eq("_id", userObjectId)).first() == null) {

            throw new IllegalArgumentException(
                    "User account was not found"
            );
        }

        MongoCollection<Document> claims =
                DBConnection
                        .getDatabase()
                        .getCollection("claims");

        MongoCollection<Document> items =
                DBConnection
                        .getDatabase()
                        .getCollection("items");

        List<Map<String, Object>> result =
                new ArrayList<>();

        for (
                Document claim :
                claims.find(
                                or(
                                        eq("claimantId", userId),
                                        eq("finderId", userId)
                                )
                        )
                        .sort(descending("createdAt"))
        ) {

            Map<String, Object> map =
                    ResponseUtil.documentToMap(claim);

            Document lostItem = findItem(
                    items,
                    claim.getString("lostItemId")
            );

            Document foundItem = findItem(
                    items,
                    claim.getString("foundItemId")
            );

            map.put(
                    "lostItemName",
                    lostItem == null
                            ? "Deleted item"
                            : safeItemName(lostItem)
            );

            map.put(
                    "foundItemName",
                    foundItem == null
                            ? "Deleted item"
                            : safeItemName(foundItem)
            );

            addItemImages(
                    map,
                    lostItem,
                    foundItem
            );

            boolean isFinder =
                    userId.equals(
                            claim.getString("finderId")
                    );

            map.put(
                    "viewerRole",
                    isFinder ? "FINDER" : "OWNER"
            );

            String status =
                    safeString(claim.get("status"))
                            .toUpperCase();

            /*
             * The ownership proof is intended for the finder.
             * The owner already knows what they submitted.
             */
            if (!isFinder && "PENDING".equals(status)) {

                map.remove("ownershipProof");
                map.remove("additionalDetails");
                map.remove("verificationAnswer");
            }

            /*
             * Contact details are returned only after approval.
             */
            if (CONTACT_VISIBLE_STATUSES.contains(status)) {

                String otherUserId = isFinder
                        ? claim.getString("claimantId")
                        : claim.getString("finderId");

                Document otherUser =
                        findUser(users, otherUserId);

                if (otherUser != null) {

                    map.put(
                            "otherPartyName",
                            safeString(otherUser.get("name"))
                    );

                    map.put(
                            "otherPartyPhone",
                            safeString(otherUser.get("phone"))
                    );

                    map.put(
                            "otherPartyEmail",
                            safeString(otherUser.get("email"))
                    );
                }
            } else {

                /*
                 * Explicitly make it clear that contacts are hidden.
                 */
                map.put("contactVisible", false);
            }

            if (CONTACT_VISIBLE_STATUSES.contains(status)) {
                map.put("contactVisible", true);
            }

            result.add(map);
        }

        ResponseUtil.json(
                exchange,
                200,
                result
        );
    }

    /*
     * PUT /api/claims?id=...&action=APPROVE&userId=...
     */
    private void updateClaim(HttpExchange exchange)
            throws IOException {

        String query =
                exchange.getRequestURI().getQuery();

        String claimId =
                queryValue(query, "id");

        String action =
                queryValue(query, "action")
                        .toUpperCase();

        String userId =
                queryValue(query, "userId");

        if (
                claimId.isBlank()
                        || action.isBlank()
                        || userId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Claim, action and user are required"
            );
        }

        ObjectId claimObjectId = parseObjectId(
                claimId,
                "Invalid claim id"
        );

        parseObjectId(
                userId,
                "Invalid user id"
        );

        MongoCollection<Document> claims =
                DBConnection
                        .getDatabase()
                        .getCollection("claims");

        MongoCollection<Document> items =
                DBConnection
                        .getDatabase()
                        .getCollection("items");

        Document claim = claims.find(
                eq("_id", claimObjectId)
        ).first();

        if (claim == null) {

            throw new IllegalArgumentException(
                    "Claim not found"
            );
        }

        boolean isFinder =
                userId.equals(
                        claim.getString("finderId")
                );

        boolean isOwner =
                userId.equals(
                        claim.getString("claimantId")
                );

        if (!isFinder && !isOwner) {

            throw new IllegalArgumentException(
                    "You are not permitted to update this claim"
            );
        }

        String currentStatus =
                safeString(claim.get("status"))
                        .toUpperCase();

        switch (action) {

            case "APPROVE" ->
                    approveClaim(
                            claims,
                            items,
                            claim,
                            claimObjectId,
                            isFinder,
                            currentStatus
                    );

            case "REJECT" ->
                    rejectClaim(
                            claims,
                            items,
                            claim,
                            claimObjectId,
                            isFinder,
                            currentStatus
                    );

            case "RETURNED" ->
                    markReturned(
                            claims,
                            items,
                            claim,
                            claimObjectId,
                            isFinder,
                            currentStatus
                    );

            case "RECEIVED" ->
                    markReceived(
                            claims,
                            items,
                            claim,
                            claimObjectId,
                            isOwner
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unknown claim action"
                    );
        }

        ResponseUtil.json(
                exchange,
                200,
                ResponseUtil.message(
                        "Claim updated successfully"
                )
        );
    }

    private void approveClaim(
            MongoCollection<Document> claims,
            MongoCollection<Document> items,
            Document claim,
            ObjectId claimObjectId,
            boolean isFinder,
            String currentStatus
    ) {

        if (!isFinder) {

            throw new IllegalArgumentException(
                    "Only the finder can approve this claim"
            );
        }

        if (!"PENDING".equals(currentStatus)) {

            throw new IllegalArgumentException(
                    "Only a pending claim can be approved"
            );
        }

        /*
         * Check whether another claim has already been approved for
         * this found item.
         */
        Document existingApproved = claims.find(
                and(
                        eq(
                                "foundItemId",
                                claim.getString("foundItemId")
                        ),
                        ne("_id", claimObjectId),
                        in(
                                "status",
                                List.of(
                                        "APPROVED",
                                        "RETURNED",
                                        "RESOLVED"
                                )
                        )
                )
        ).first();

        if (existingApproved != null) {

            throw new IllegalArgumentException(
                    "Another claim has already been approved for this item"
            );
        }

        UpdateResult result = claims.updateOne(
                and(
                        eq("_id", claimObjectId),
                        eq("status", "PENDING")
                ),
                new Document(
                        "$set",
                        new Document()
                                .append("status", "APPROVED")
                                .append(
                                        "approvedAt",
                                        Instant.now().toString()
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        if (result.getModifiedCount() == 0) {

            throw new IllegalArgumentException(
                    "Claim status changed. Refresh the page and try again"
            );
        }

        updateBothItems(
                items,
                claim,
                "CLAIM_APPROVED"
        );

        /*
         * Reject other pending claims for the same found item.
         */
        claims.updateMany(
                and(
                        eq(
                                "foundItemId",
                                claim.getString("foundItemId")
                        ),
                        ne("_id", claimObjectId),
                        eq("status", "PENDING")
                ),
                new Document(
                        "$set",
                        new Document()
                                .append("status", "REJECTED")
                                .append(
                                        "rejectionReason",
                                        "Another claim was approved"
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        NotificationUtil.create(
                claim.getString("claimantId"),
                "CLAIM_APPROVED",
                "Your claim was approved. Open the Claims page to contact the finder.",
                claimObjectId.toHexString()
        );
    }

    private void rejectClaim(
            MongoCollection<Document> claims,
            MongoCollection<Document> items,
            Document claim,
            ObjectId claimObjectId,
            boolean isFinder,
            String currentStatus
    ) {

        if (!isFinder) {

            throw new IllegalArgumentException(
                    "Only the finder can reject this claim"
            );
        }

        if (!"PENDING".equals(currentStatus)) {

            throw new IllegalArgumentException(
                    "Only a pending claim can be rejected"
            );
        }

        claims.updateOne(
                and(
                        eq("_id", claimObjectId),
                        eq("status", "PENDING")
                ),
                new Document(
                        "$set",
                        new Document()
                                .append("status", "REJECTED")
                                .append(
                                        "rejectedAt",
                                        Instant.now().toString()
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        /*
         * Return items to ACTIVE only when there is no other active claim.
         */
        Document anotherActiveClaim = claims.find(
                and(
                        eq(
                                "foundItemId",
                                claim.getString("foundItemId")
                        ),
                        ne("_id", claimObjectId),
                        in("status", ACTIVE_CLAIM_STATUSES)
                )
        ).first();

        if (anotherActiveClaim == null) {

            updateBothItems(
                    items,
                    claim,
                    "ACTIVE"
            );
        }

        NotificationUtil.create(
                claim.getString("claimantId"),
                "CLAIM_REJECTED",
                "Your claim was rejected by the finder.",
                claimObjectId.toHexString()
        );
    }

    private void markReturned(
            MongoCollection<Document> claims,
            MongoCollection<Document> items,
            Document claim,
            ObjectId claimObjectId,
            boolean isFinder,
            String currentStatus
    ) {

        if (!isFinder) {

            throw new IllegalArgumentException(
                    "Only the finder can mark the item as returned"
            );
        }

        if (!"APPROVED".equals(currentStatus)) {

            throw new IllegalArgumentException(
                    "Approve the claim before returning the item"
            );
        }

        claims.updateOne(
                and(
                        eq("_id", claimObjectId),
                        eq("status", "APPROVED")
                ),
                new Document(
                        "$set",
                        new Document()
                                .append("finderReturned", true)
                                .append("status", "RETURNED")
                                .append(
                                        "returnedAt",
                                        Instant.now().toString()
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        updateBothItems(
                items,
                claim,
                "RETURNED"
        );

        NotificationUtil.create(
                claim.getString("claimantId"),
                "ITEM_RETURNED",
                "The finder marked the item as returned. Confirm only after receiving it.",
                claimObjectId.toHexString()
        );
    }

    private void markReceived(
            MongoCollection<Document> claims,
            MongoCollection<Document> items,
            Document claim,
            ObjectId claimObjectId,
            boolean isOwner
    ) {

        if (!isOwner) {

            throw new IllegalArgumentException(
                    "Only the owner can confirm receiving the item"
            );
        }

        if (
                !"RETURNED".equals(
                        safeString(claim.get("status"))
                                .toUpperCase()
                )
                        || !Boolean.TRUE.equals(
                                claim.getBoolean("finderReturned")
                )
        ) {

            throw new IllegalArgumentException(
                    "The finder must mark the item as returned first"
            );
        }

        claims.updateOne(
                and(
                        eq("_id", claimObjectId),
                        eq("status", "RETURNED"),
                        eq("finderReturned", true)
                ),
                new Document(
                        "$set",
                        new Document()
                                .append("ownerReceived", true)
                                .append("status", "RESOLVED")
                                .append(
                                        "resolvedAt",
                                        Instant.now().toString()
                                )
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );

        updateBothItems(
                items,
                claim,
                "RESOLVED"
        );

        NotificationUtil.create(
                claim.getString("finderId"),
                "ITEM_RESOLVED",
                "The owner confirmed receipt. The lost and found posts are now resolved.",
                claimObjectId.toHexString()
        );
    }

    private void updateBothItems(
            MongoCollection<Document> items,
            Document claim,
            String status
    ) {

        ObjectId lostItemId = parseObjectId(
                claim.getString("lostItemId"),
                "Invalid lost item id in claim"
        );

        ObjectId foundItemId = parseObjectId(
                claim.getString("foundItemId"),
                "Invalid found item id in claim"
        );

        items.updateMany(
                in("_id", lostItemId, foundItemId),
                new Document(
                        "$set",
                        new Document()
                                .append("status", status)
                                .append(
                                        "updatedAt",
                                        Instant.now().toString()
                                )
                )
        );
    }

    private Document findItem(
            MongoCollection<Document> items,
            String itemId
    ) {

        Optional<ObjectId> objectId =
                safeObjectId(itemId);

        return objectId
                .map(id -> items.find(eq("_id", id)).first())
                .orElse(null);
    }

    private Document findUser(
            MongoCollection<Document> users,
            String userId
    ) {

        Optional<ObjectId> objectId =
                safeObjectId(userId);

        return objectId
                .map(id -> users.find(eq("_id", id)).first())
                .orElse(null);
    }

    private void validateRequiredIds(
            String lostItemId,
            String foundItemId,
            String claimantId
    ) {

        if (
                lostItemId.isBlank()
                        || foundItemId.isBlank()
                        || claimantId.isBlank()
        ) {

            throw new IllegalArgumentException(
                    "Lost item, found item and claimant are required"
            );
        }
    }

    private void validateClaimText(
            String ownershipProof,
            String additionalDetails,
            String message
    ) {

        if (ownershipProof.isBlank()) {

            throw new IllegalArgumentException(
                    "Unique identification details are required"
            );
        }

        if (ownershipProof.length() < 10) {

            throw new IllegalArgumentException(
                    "Enter at least 10 characters for the unique identification"
            );
        }

        if (ownershipProof.length() > 500) {

            throw new IllegalArgumentException(
                    "Unique identification must not exceed 500 characters"
            );
        }

        if (
                !additionalDetails.isBlank()
                        && additionalDetails.length() > 500
        ) {

            throw new IllegalArgumentException(
                    "Additional details must not exceed 500 characters"
            );
        }

        if (!message.isBlank() && message.length() > 300) {

            throw new IllegalArgumentException(
                    "Message must not exceed 300 characters"
            );
        }
    }

   
    private ObjectId parseObjectId(
            String value,
            String errorMessage
    ) {

        if (
                value == null
                        || !ObjectId.isValid(value)
        ) {

            throw new IllegalArgumentException(
                    errorMessage
            );
        }

        return new ObjectId(value);
    }

    private Optional<ObjectId> safeObjectId(
            String value
    ) {

        if (
                value == null
                        || !ObjectId.isValid(value)
        ) {

            return Optional.empty();
        }

        return Optional.of(
                new ObjectId(value)
        );
    }

    private String safeItemName(Document item) {

        String itemName =
                item.getString("itemName");

        return itemName == null || itemName.isBlank()
                ? "Unnamed item"
                : itemName;
    }

    private String normalize(String value) {

        return value == null
                ? ""
                : value.trim().toLowerCase();
    }

    private String safeString(Object value) {

        return value == null
                ? ""
                : value.toString().trim();
    }

    private String value(
            Map<String, Object> map,
            String key
    ) {

        Object value = map.get(key);

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

            if (
                    pair.length == 2
                            && URLDecoder.decode(
                                    pair[0],
                                    StandardCharsets.UTF_8
                    ).equals(key)
            ) {

                return URLDecoder.decode(
                        pair[1],
                        StandardCharsets.UTF_8
                );
            }
        }

        return "";
    }
}

