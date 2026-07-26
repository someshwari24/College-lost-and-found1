package com.lostfound.algorithm;

import org.bson.Document;

import java.util.HashSet;
import java.util.Set;

public final class MatchingAlgorithm {

    private MatchingAlgorithm() {
    }

    /*
     * Final scoring:
     *
     * Category     = 30
     * Item name    = 25
     * Color        = 15
     * Brand        = 10
     * Location     = 10
     * Description  = 10
     *
     * Total        = 100
     */
    public static double calculateScore(
            Document first,
            Document second
    ) {
        if (first == null || second == null) {
            return 0.0;
        }

        double score = 0.0;

        /*
         * Category exact match
         */
        if (same(first, second, "category")) {
            score += 30.0;
        }

        /*
         * Item name similarity:
         * use the better result between Jaccard and Levenshtein
         */
        double itemNameSimilarity = Math.max(
                jaccardSimilarity(
                        value(first, "itemName"),
                        value(second, "itemName")
                ),
                levenshteinSimilarity(
                        value(first, "itemName"),
                        value(second, "itemName")
                )
        );

        score += itemNameSimilarity * 25.0;

        /*
         * Color exact match
         */
        if (same(first, second, "color")) {
            score += 15.0;
        }

        /*
         * Brand similarity
         */
        double brandSimilarity = Math.max(
                exactSimilarity(
                        value(first, "brand"),
                        value(second, "brand")
                ),
                levenshteinSimilarity(
                        value(first, "brand"),
                        value(second, "brand")
                )
        );

        score += brandSimilarity * 10.0;

        /*
         * Location similarity
         */
        double locationSimilarity = Math.max(
                exactSimilarity(
                        value(first, "location"),
                        value(second, "location")
                ),
                jaccardSimilarity(
                        value(first, "location"),
                        value(second, "location")
                )
        );

        score += locationSimilarity * 10.0;

        /*
         * Description similarity
         */
        double descriptionSimilarity = jaccardSimilarity(
                value(first, "description"),
                value(second, "description")
        );

        score += descriptionSimilarity * 10.0;

        /*
         * Keep result between 0 and 100
         */
        return Math.max(
                0.0,
                Math.min(score, 100.0)
        );
    }

    /*
     * Converts score into a readable level.
     */
    public static String level(double score) {
        if (score >= 70.0) {
            return "STRONG";
        }

        if (score >= 50.0) {
            return "POSSIBLE";
        }

        return "LOW";
    }

    /*
     * Checks whether two fields match exactly,
     * ignoring uppercase/lowercase and extra spaces.
     */
    private static boolean same(
            Document first,
            Document second,
            String field
    ) {
        String firstValue = normalize(
                value(first, field)
        );

        String secondValue = normalize(
                value(second, field)
        );

        return !firstValue.isBlank()
                && !secondValue.isBlank()
                && firstValue.equals(secondValue);
    }

    /*
     * Returns 1.0 for exact match and 0.0 otherwise.
     */
    private static double exactSimilarity(
            String first,
            String second
    ) {
        first = normalize(first);
        second = normalize(second);

        if (first.isBlank() || second.isBlank()) {
            return 0.0;
        }

        return first.equals(second)
                ? 1.0
                : 0.0;
    }

    /*
     * Jaccard similarity compares common words.
     *
     * Formula:
     * intersection / union
     *
     * Example:
     * "black samsung phone"
     * "samsung black mobile"
     *
     * Common words:
     * black, samsung
     */
    private static double jaccardSimilarity(
            String first,
            String second
    ) {
        Set<String> firstWords = words(first);
        Set<String> secondWords = words(second);

        if (firstWords.isEmpty() || secondWords.isEmpty()) {
            return 0.0;
        }

        Set<String> intersection =
                new HashSet<>(firstWords);

        intersection.retainAll(secondWords);

        Set<String> union =
                new HashSet<>(firstWords);

        union.addAll(secondWords);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size()
                / union.size();
    }

    /*
     * Levenshtein similarity handles spelling mistakes.
     *
     * Examples:
     * samsung / samsng
     * headphone / headphones
     */
    private static double levenshteinSimilarity(
            String first,
            String second
    ) {
        first = normalize(first);
        second = normalize(second);

        if (first.isBlank() || second.isBlank()) {
            return 0.0;
        }

        if (first.equals(second)) {
            return 1.0;
        }

        int firstLength = first.length();
        int secondLength = second.length();

        int[][] dp = new int[firstLength + 1][secondLength + 1];

        for (int i = 0; i <= firstLength; i++) {
            dp[i][0] = i;
        }

        for (int j = 0; j <= secondLength; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= firstLength; i++) {
            for (int j = 1; j <= secondLength; j++) {

                int cost =
                        first.charAt(i - 1)
                                == second.charAt(j - 1)
                                ? 0
                                : 1;

                int deletion =
                        dp[i - 1][j] + 1;

                int insertion =
                        dp[i][j - 1] + 1;

                int replacement =
                        dp[i - 1][j - 1] + cost;

                dp[i][j] = Math.min(
                        Math.min(deletion, insertion),
                        replacement
                );
            }
        }

        int distance =
                dp[firstLength][secondLength];

        int maximumLength =
                Math.max(firstLength, secondLength);

        return 1.0
                - ((double) distance / maximumLength);
    }

    /*
     * Converts text into a set of meaningful words.
     */
    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();

        String normalized = normalize(text)
                .replaceAll("[^a-z0-9 ]", " ");

        for (String word : normalized.split("\\s+")) {
            if (word.length() >= 2) {
                result.add(word);
            }
        }

        return result;
    }

    /*
     * Safely reads a value from MongoDB Document.
     */
    private static String value(
            Document document,
            String key
    ) {
        if (document == null) {
            return "";
        }

        Object value = document.get(key);

        return value == null
                ? ""
                : value.toString().trim();
    }

    /*
     * Normalizes text for comparison.
     */
    private static String normalize(String text) {
        if (text == null) {
            return "";
        }

        return text
                .trim()
                .toLowerCase()
                .replaceAll("\\s+", " ");
    }
}