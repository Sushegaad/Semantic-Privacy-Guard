package com.semanticprivacyguard.stream;

import com.semanticprivacyguard.model.PIIType;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Aggregated statistics returned by {@link StreamProcessor} after processing a
 * stream, file, or sequence of lines.
 *
 * <p>Because stream processing never holds the full document in memory, a
 * per-line {@link com.semanticprivacyguard.model.RedactionResult} is not
 * available after the fact.  This summary provides the document-level counts
 * that callers need for auditing, alerting, and observability.</p>
 *
 * <pre>{@code
 * StreamRedactionSummary summary = spg.redactStream(inputStream, outputStream);
 *
 * System.out.printf("Scanned %d lines, found PII on %d (%.1f%%)%n",
 *     summary.getTotalLines(),
 *     summary.getLinesWithPII(),
 *     summary.getPIILineRatio() * 100);
 *
 * summary.getMatchCountByType()
 *        .forEach((type, count) ->
 *            System.out.printf("  %s: %d%n", type.getLabel(), count));
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.1.0
 */
public final class StreamRedactionSummary {

    private final long               totalLines;
    private final long               linesWithPII;
    private final long               totalMatches;
    private final long               processingTimeMs;
    private final Map<PIIType, Long> matchCountByType;

    /**
     * Constructs a {@code StreamRedactionSummary}.
     *
     * @param totalLines       total number of lines read
     * @param linesWithPII     number of lines that contained at least one PII match
     * @param totalMatches     total number of individual PII matches across all lines
     * @param processingTimeMs wall-clock time for the full processing pass in ms
     * @param matchCountByType per-type match counts (defensive copy is taken)
     */
    public StreamRedactionSummary(long               totalLines,
                                  long               linesWithPII,
                                  long               totalMatches,
                                  long               processingTimeMs,
                                  Map<PIIType, Long>  matchCountByType) {
        this.totalLines        = totalLines;
        this.linesWithPII      = linesWithPII;
        this.totalMatches      = totalMatches;
        this.processingTimeMs  = processingTimeMs;
        this.matchCountByType  = Collections.unmodifiableMap(
            matchCountByType == null
                ? Collections.emptyMap()
                : new EnumMap<>(matchCountByType));
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns the total number of lines read from the source. */
    public long getTotalLines()       { return totalLines;       }

    /** Returns the number of lines on which at least one PII match was found. */
    public long getLinesWithPII()     { return linesWithPII;     }

    /** Returns the total number of individual PII matches across all lines. */
    public long getTotalMatches()     { return totalMatches;     }

    /** Returns the wall-clock processing time for the full pass in milliseconds. */
    public long getProcessingTimeMs() { return processingTimeMs; }

    /**
     * Returns an unmodifiable map from {@link PIIType} to the number of times
     * that type was detected across all lines.
     */
    public Map<PIIType, Long> getMatchCountByType() { return matchCountByType; }

    /** Returns {@code true} if at least one PII match was found anywhere in the stream. */
    public boolean hasPII() { return totalMatches > 0; }

    /**
     * Returns the fraction of lines that contained PII, in the range [0.0, 1.0].
     * Returns 0.0 if no lines were read.
     */
    public double getPIILineRatio() {
        return totalLines == 0 ? 0.0 : (double) linesWithPII / totalLines;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    /**
     * Returns a human-readable one-line summary.
     *
     * <pre>
     * StreamRedactionSummary[lines=1000, linesWithPII=42, matches=67, timeMs=38]
     * </pre>
     */
    @Override
    public String toString() {
        return String.format(
            "StreamRedactionSummary[lines=%d, linesWithPII=%d, matches=%d, timeMs=%d]",
            totalLines, linesWithPII, totalMatches, processingTimeMs);
    }
}
