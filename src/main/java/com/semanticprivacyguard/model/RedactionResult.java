package com.semanticprivacyguard.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable result object returned by {@code SemanticPrivacyGuard.redact()}.
 *
 * <p>Contains the sanitised text, the full list of detections, a reverse-lookup
 * map from replacement token back to the original value (for authorised
 * de-tokenisation), and summary statistics.</p>
 *
 * <pre>{@code
 * RedactionResult result = spg.redact(rawText);
 *
 * String safe = result.getRedactedText();          // text safe to send to LLM
 * int    n    = result.getMatchCount();            // number of PII items found
 * String orig = result.getReverseMap()
 *                     .get("[EMAIL_1]");            // "alice@example.com"
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class RedactionResult {

    private final String            originalText;
    private final String            redactedText;
    private final List<PIIMatch>    matches;

    /**
     * Reverse-lookup map: replacement token  →  original value.
     * Example entry: {@code "[EMAIL_1]" → "alice@example.com"}.
     * May be empty if the guard is configured not to build it.
     */
    private final Map<String, String> reverseMap;

    /** Elapsed wall-clock time for the full detection + redaction pass, in ms. */
    private final long processingTimeMs;

    /**
     * Constructs a {@code RedactionResult}.
     *
     * @param originalText    the raw input (never {@code null})
     * @param redactedText    the sanitised output (never {@code null})
     * @param matches         unmodifiable list of detections (never {@code null})
     * @param reverseMap      token → original map (never {@code null})
     * @param processingTimeMs wall-clock duration in milliseconds
     */
    public RedactionResult(String originalText,
                           String redactedText,
                           List<PIIMatch> matches,
                           Map<String, String> reverseMap,
                           long processingTimeMs) {
        this.originalText     = Objects.requireNonNull(originalText);
        this.redactedText     = Objects.requireNonNull(redactedText);
        this.matches          = Collections.unmodifiableList(
                                    Objects.requireNonNull(matches));
        this.reverseMap       = Collections.unmodifiableMap(
                                    Objects.requireNonNull(reverseMap));
        this.processingTimeMs = processingTimeMs;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the original, unmodified input text. */
    public String getOriginalText()     { return originalText;     }

    /** Returns the sanitised text with PII replaced by tokens. */
    public String getRedactedText()     { return redactedText;     }

    /** Returns an unmodifiable list of all PII matches, sorted by position. */
    public List<PIIMatch> getMatches()  { return matches;          }

    /**
     * Returns an unmodifiable reverse-lookup map.
     * Keys are replacement tokens (e.g. {@code "[EMAIL_1]"});
     * values are the original PII strings.
     */
    public Map<String, String> getReverseMap() { return reverseMap; }

    /** Returns the number of PII items detected. */
    public int getMatchCount()          { return matches.size();   }

    /** Returns the wall-clock processing time in milliseconds. */
    public long getProcessingTimeMs()   { return processingTimeMs; }

    /** Returns {@code true} if at least one PII item was detected. */
    public boolean containsPII()        { return !matches.isEmpty(); }

    /**
     * Returns {@code true} if the text was unchanged (no PII detected).
     * Equivalent to {@code !containsPII()}.
     */
    public boolean isClean()            { return matches.isEmpty(); }

    // ── Summary ───────────────────────────────────────────────────────────────

    /**
     * Returns a one-line human-readable summary of the redaction pass.
     *
     * <pre>
     * RedactionResult[matches=3, types=[EMAIL, PHONE, SSN], timeMs=2]
     * </pre>
     */
    @Override
    public String toString() {
        long distinctTypes = matches.stream()
                                    .map(PIIMatch::getType)
                                    .distinct()
                                    .count();
        return String.format(
            "RedactionResult[matches=%d, distinctTypes=%d, timeMs=%d]",
            matches.size(), distinctTypes, processingTimeMs);
    }
}
