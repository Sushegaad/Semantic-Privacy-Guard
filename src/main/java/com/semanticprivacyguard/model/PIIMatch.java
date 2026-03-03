package com.semanticprivacyguard.model;

import java.util.Objects;

/**
 * Represents a single detected occurrence of PII within the original text.
 *
 * <p>Instances are immutable and are created by detector implementations.
 * A match records the exact character span {@code [startIndex, endIndex)},
 * the raw matched value, the classified type, the detection source, and a
 * confidence score in the range {@code [0.0, 1.0]}.</p>
 *
 * <pre>{@code
 * PIIMatch m = new PIIMatch(PIIType.EMAIL, "alice@example.com",
 *                           10, 27, DetectionSource.HEURISTIC, 1.0);
 * System.out.println(m.getType());       // EMAIL
 * System.out.println(m.getStartIndex()); // 10
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class PIIMatch implements Comparable<PIIMatch> {

    /** How a particular match was discovered. */
    public enum DetectionSource {
        /** Pattern matched a hard-coded regular expression. */
        HEURISTIC,
        /** Classified as PII by the Naive Bayes context model. */
        ML,
        /** Matched both heuristic and ML paths. */
        HYBRID
    }

    private final PIIType         type;
    private final String          value;
    private final int             startIndex;
    private final int             endIndex;
    private final DetectionSource source;
    private final double          confidence;

    /**
     * Constructs a new {@code PIIMatch}.
     *
     * @param type       the PII category (never {@code null})
     * @param value      the raw matched string (never {@code null})
     * @param startIndex inclusive start offset in the original text
     * @param endIndex   exclusive end offset in the original text
     * @param source     how the match was found (never {@code null})
     * @param confidence detection confidence in {@code [0.0, 1.0]}
     */
    public PIIMatch(PIIType type, String value,
                    int startIndex, int endIndex,
                    DetectionSource source, double confidence) {
        Objects.requireNonNull(type,   "type must not be null");
        Objects.requireNonNull(value,  "value must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (startIndex < 0 || endIndex < startIndex) {
            throw new IllegalArgumentException(
                "Invalid span: [" + startIndex + ", " + endIndex + ")");
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                "confidence must be in [0.0, 1.0], got: " + confidence);
        }
        this.type       = type;
        this.value      = value;
        this.startIndex = startIndex;
        this.endIndex   = endIndex;
        this.source     = source;
        this.confidence = confidence;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public PIIType         getType()       { return type;       }
    public String          getValue()      { return value;      }
    public int             getStartIndex() { return startIndex; }
    public int             getEndIndex()   { return endIndex;   }
    public DetectionSource getSource()     { return source;     }
    public double          getConfidence() { return confidence; }

    /** Returns the length of the matched span. */
    public int length() { return endIndex - startIndex; }

    /**
     * Returns {@code true} if this match overlaps with {@code other}.
     * Overlapping matches are de-duplicated by the composite detector.
     */
    public boolean overlaps(PIIMatch other) {
        return this.startIndex < other.endIndex
            && other.startIndex < this.endIndex;
    }

    // ── Comparable / equals / hashCode ────────────────────────────────────────

    /** Orders matches by their start position, then by end position descending
     *  (longest match first for the same start). */
    @Override
    public int compareTo(PIIMatch other) {
        int cmp = Integer.compare(this.startIndex, other.startIndex);
        if (cmp != 0) return cmp;
        return Integer.compare(other.endIndex, this.endIndex); // longer first
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PIIMatch o)) return false;
        return startIndex == o.startIndex
            && endIndex   == o.endIndex
            && type       == o.type
            && value.equals(o.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value, startIndex, endIndex);
    }

    @Override
    public String toString() {
        return String.format("PIIMatch{type=%s, value='%s', span=[%d,%d), source=%s, confidence=%.2f}",
                             type, value, startIndex, endIndex, source, confidence);
    }
}
