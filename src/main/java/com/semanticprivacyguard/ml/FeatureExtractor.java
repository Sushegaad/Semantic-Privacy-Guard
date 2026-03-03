package com.semanticprivacyguard.ml;

import com.semanticprivacyguard.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a bag-of-words feature vector for a candidate token and its
 * surrounding context window.
 *
 * <h2>Feature categories</h2>
 * <ol>
 *   <li><b>Context words</b> — the {@code windowSize} words before and after
 *       the candidate token, lowercased.</li>
 *   <li><b>Structural features</b> — binary flags (encoded as string tokens)
 *       that capture surface properties of the candidate:
 *       {@code HAS_DIGIT}, {@code ALL_CAPS}, {@code IS_TITLE_CASE},
 *       {@code HAS_HYPHEN}, {@code HIGH_ENTROPY}.</li>
 *   <li><b>Preceding keyword</b> — the immediately preceding word lowercased,
 *       prefixed with {@code "PREV:"} to distinguish it from context words.
 *       This is the strongest single signal for names: words like
 *       {@code "dear"}, {@code "contact"}, {@code "mr"}, etc.</li>
 *   <li><b>Token length bucket</b> — {@code "LEN_SHORT"} (&lt; 4),
 *       {@code "LEN_MEDIUM"} (4–8), {@code "LEN_LONG"} (&gt; 8).</li>
 * </ol>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class FeatureExtractor {

    private static final int DEFAULT_WINDOW = 3;
    private static final double HIGH_ENTROPY_THRESHOLD = 3.8;

    private final int windowSize;

    /** Creates an extractor with the default 3-word context window. */
    public FeatureExtractor() {
        this(DEFAULT_WINDOW);
    }

    /**
     * Creates an extractor with a custom context window.
     *
     * @param windowSize number of context tokens on each side
     */
    public FeatureExtractor(int windowSize) {
        if (windowSize < 0) throw new IllegalArgumentException("windowSize must be >= 0");
        this.windowSize = windowSize;
    }

    /**
     * Extracts features for the token at {@code tokenIndex} in {@code spans}.
     *
     * @param text       full input text
     * @param spans      token spans as produced by {@link TextUtils#tokenSpans}
     * @param tokenIndex index of the candidate token
     * @return non-empty feature list
     */
    public List<String> extract(String text, List<int[]> spans, int tokenIndex) {
        List<String> features = new ArrayList<>();

        int[]  span  = spans.get(tokenIndex);
        String token = text.substring(span[0], span[1]);

        // 1. Context words
        String ctx = TextUtils.contextWindow(text, spans, tokenIndex, windowSize);
        if (!ctx.isEmpty()) {
            for (String w : ctx.split("\\s+")) {
                if (!w.isBlank()) features.add(w);
            }
        }

        // 2. Preceding keyword feature
        if (tokenIndex > 0) {
            int[] prevSpan = spans.get(tokenIndex - 1);
            String prev = text.substring(prevSpan[0], prevSpan[1]).toLowerCase();
            features.add("PREV:" + prev);
        }

        // 3. Structural features
        if (token.chars().anyMatch(Character::isDigit))
            features.add("HAS_DIGIT");
        if (token.equals(token.toUpperCase()) && token.length() > 1)
            features.add("ALL_CAPS");
        if (Character.isUpperCase(token.charAt(0)) && token.length() > 1
                && token.substring(1).equals(token.substring(1).toLowerCase()))
            features.add("IS_TITLE_CASE");
        if (token.contains("-"))
            features.add("HAS_HYPHEN");
        if (TextUtils.shannonEntropy(token) >= HIGH_ENTROPY_THRESHOLD)
            features.add("HIGH_ENTROPY");
        if (token.matches(".*[.@].*"))
            features.add("HAS_SPECIAL_CHAR");

        // 4. Length bucket
        int len = token.length();
        if      (len < 4)  features.add("LEN_SHORT");
        else if (len <= 8) features.add("LEN_MEDIUM");
        else               features.add("LEN_LONG");

        // 5. Character composition ratios (discretised)
        long upper  = token.chars().filter(Character::isUpperCase).count();
        long digits = token.chars().filter(Character::isDigit).count();
        if ((double) upper  / len > 0.5) features.add("MOSTLY_UPPER");
        if ((double) digits / len > 0.5) features.add("MOSTLY_DIGITS");

        return features;
    }
}
