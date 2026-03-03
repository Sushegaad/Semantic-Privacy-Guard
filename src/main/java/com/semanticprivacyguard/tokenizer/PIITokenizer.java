package com.semanticprivacyguard.tokenizer;

import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIType;

import java.util.*;

/**
 * Replaces detected PII spans in text with structured replacement tokens.
 *
 * <h2>Token format</h2>
 *
 * <p>Tokens take the form {@code [TYPE_N]} where {@code TYPE} is the
 * {@link PIIType#getLabel()} and {@code N} is a 1-based counter scoped to
 * each type within a single redaction call.  Examples:</p>
 * <ul>
 *   <li>{@code [EMAIL_1]}, {@code [EMAIL_2]}</li>
 *   <li>{@code [PERSON_NAME_1]}</li>
 *   <li>{@code [SSN_1]}</li>
 * </ul>
 *
 * <p>The token format is deliberately chosen so that an LLM receiving the
 * redacted text can still understand the sentence structure, infer the type
 * of missing information, and work with the anonymised context — without ever
 * seeing the original values.</p>
 *
 * <h2>De-tokenisation</h2>
 *
 * <p>The {@link #redact} method returns a {@code Map<String,String>} that maps
 * each replacement token back to the original value, enabling authorised
 * downstream systems to reconstruct the original text if needed.</p>
 *
 * <h2>Redaction modes</h2>
 *
 * <p>Three built-in modes are supported, configurable at construction time:</p>
 * <ul>
 *   <li>{@link RedactionMode#TOKEN} — structured {@code [TYPE_N]} tokens
 *       (default; ideal for LLM pipelines).</li>
 *   <li>{@link RedactionMode#MASK} — replaces the value with a fixed-width
 *       sequence of {@code █} characters.</li>
 *   <li>{@link RedactionMode#BLANK} — replaces the value with a single
 *       {@code [REDACTED]} placeholder.</li>
 * </ul>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class PIITokenizer {

    /** Determines how detected PII is represented in the output. */
    public enum RedactionMode {
        /** {@code [TYPE_N]} — preserves type info for downstream LLMs. */
        TOKEN,
        /** {@code ████████} — fixed-width mask, length matches original. */
        MASK,
        /** {@code [REDACTED]} — generic opaque placeholder. */
        BLANK
    }

    private static final char MASK_CHAR = '█';
    private static final String BLANK_TOKEN = "[REDACTED]";

    private final RedactionMode mode;

    /** Creates a tokenizer using {@link RedactionMode#TOKEN} (default). */
    public PIITokenizer() {
        this(RedactionMode.TOKEN);
    }

    /**
     * Creates a tokenizer using the specified redaction mode.
     *
     * @param mode the redaction mode (never {@code null})
     */
    public PIITokenizer(RedactionMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode must not be null");
    }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Applies all matches to {@code text}, returning the redacted string and a
     * reverse-lookup map.
     *
     * <p>The caller is responsible for ensuring that {@code matches} are
     * non-overlapping and sorted by start index (as guaranteed by
     * {@link com.semanticprivacyguard.detector.CompositeDetector}).</p>
     *
     * @param text    the original text (never {@code null})
     * @param matches non-overlapping, sorted list of PII matches
     * @return a {@link RedactionOutput} containing the redacted text and the
     *         token-to-original reverse map
     */
    public RedactionOutput redact(String text, List<PIIMatch> matches) {
        Objects.requireNonNull(text, "text must not be null");
        if (matches == null || matches.isEmpty()) {
            return new RedactionOutput(text, Collections.emptyMap());
        }

        StringBuilder     sb         = new StringBuilder(text.length());
        Map<String,String> reverseMap = new LinkedHashMap<>();
        Map<PIIType,Integer> counters = new EnumMap<>(PIIType.class);

        int cursor = 0;
        for (PIIMatch m : matches) {
            // Append text between last match end and this match start
            if (m.getStartIndex() > cursor) {
                sb.append(text, cursor, m.getStartIndex());
            }

            String token = buildToken(m, counters);
            sb.append(token);
            reverseMap.put(token, m.getValue());
            cursor = m.getEndIndex();
        }

        // Append any trailing text after the last match
        if (cursor < text.length()) {
            sb.append(text, cursor, text.length());
        }

        return new RedactionOutput(sb.toString(), reverseMap);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String buildToken(PIIMatch match, Map<PIIType, Integer> counters) {
        return switch (mode) {
            case TOKEN -> {
                int n = counters.merge(match.getType(), 1, Integer::sum);
                yield "[" + match.getType().getLabel() + "_" + n + "]";
            }
            case MASK -> MASK_CHAR
                    + String.valueOf(MASK_CHAR).repeat(
                        Math.max(0, match.getValue().length() - 1));
            case BLANK -> BLANK_TOKEN;
        };
    }

    // ── Inner result type ─────────────────────────────────────────────────────

    /**
     * Holds the redacted text string and the token → original reverse map.
     */
    public record RedactionOutput(String redactedText,
                                   Map<String, String> reverseMap) {}
}
