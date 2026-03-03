package com.semanticprivacyguard.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Stateless text utility methods shared across the SPG pipeline.
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class TextUtils {

    private TextUtils() { /* utility class */ }

    // ── Luhn Algorithm ────────────────────────────────────────────────────────

    /**
     * Validates a payment card number using the Luhn (mod-10) algorithm.
     *
     * @param digits the card number with all non-digit characters removed
     * @return {@code true} if the number passes the Luhn check
     */
    public static boolean luhnCheck(String digits) {
        if (digits == null || digits.isEmpty()) return false;
        int sum     = 0;
        boolean alt = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') return false;
            int n = c - '0';
            if (alt) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alt  = !alt;
        }
        return (sum % 10) == 0;
    }

    // ── Tokenization helpers ──────────────────────────────────────────────────

    /**
     * Splits {@code text} into whitespace-delimited tokens while preserving
     * the original character offsets of each token.
     *
     * @param text the input string
     * @return list of {@code int[]{startInclusive, endExclusive}} spans
     */
    public static List<int[]> tokenSpans(String text) {
        List<int[]> spans = new ArrayList<>();
        int i = 0;
        int len = text.length();
        while (i < len) {
            // skip whitespace
            while (i < len && Character.isWhitespace(text.charAt(i))) i++;
            if (i >= len) break;
            int start = i;
            // consume non-whitespace
            while (i < len && !Character.isWhitespace(text.charAt(i))) i++;
            spans.add(new int[]{start, i});
        }
        return spans;
    }

    /**
     * Returns up to {@code windowSize} words on each side of the word at
     * {@code tokenIndex} in the list of token spans, as a single concatenated
     * lowercase string (useful as a feature vector for the ML layer).
     *
     * @param text       the full text
     * @param spans      pre-computed token spans from {@link #tokenSpans}
     * @param tokenIndex index of the token whose context window is wanted
     * @param windowSize number of tokens to include on each side
     * @return context string, never {@code null}
     */
    public static String contextWindow(String text, List<int[]> spans,
                                       int tokenIndex, int windowSize) {
        StringBuilder sb = new StringBuilder();
        int lo = Math.max(0, tokenIndex - windowSize);
        int hi = Math.min(spans.size() - 1, tokenIndex + windowSize);
        for (int j = lo; j <= hi; j++) {
            if (j == tokenIndex) continue; // skip the token itself
            int[] span = spans.get(j);
            sb.append(text, span[0], span[1]).append(' ');
        }
        return sb.toString().toLowerCase().trim();
    }

    // ── String helpers ────────────────────────────────────────────────────────

    /**
     * Strips all non-digit characters from {@code s} and returns the result.
     * Useful for normalising phone and card numbers before validation.
     */
    public static String digitsOnly(String s) {
        if (s == null) return "";
        return s.replaceAll("\\D", "");
    }

    /**
     * Returns {@code true} if {@code s} consists entirely of characters in
     * the set {@code [a-zA-Z0-9]}.
     */
    public static boolean isAlphanumeric(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c)) return false;
        }
        return true;
    }

    /**
     * Truncates {@code s} to at most {@code maxLen} characters, appending
     * {@code "…"} if truncation occurred.
     */
    public static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "\u2026";
    }

    /**
     * Counts the number of uppercase letters in {@code s}.
     * Used as a feature for API key classification.
     */
    public static int countUppercase(String s) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) count++;
        }
        return count;
    }

    /**
     * Returns the Shannon entropy (bits per character) of {@code s}.
     * High entropy strings are more likely to be random keys/tokens.
     *
     * @param s input string
     * @return entropy in bits, or {@code 0.0} for empty input
     */
    public static double shannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        int[] freq = new int[256];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 256) freq[c]++;
        }
        double entropy = 0.0;
        double len = s.length();
        for (int f : freq) {
            if (f > 0) {
                double p = f / len;
                entropy -= p * (Math.log(p) / Math.log(2));
            }
        }
        return entropy;
    }
}
