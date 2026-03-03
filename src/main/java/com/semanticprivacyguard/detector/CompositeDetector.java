package com.semanticprivacyguard.detector;

import com.semanticprivacyguard.model.PIIMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Combines the outputs of multiple {@link PIIDetector} implementations and
 * returns a de-duplicated, non-overlapping list of matches.
 *
 * <h2>De-duplication strategy</h2>
 *
 * <ol>
 *   <li>Sort all raw matches by start position; for the same start, prefer
 *       the longer span (greedy-longest match).</li>
 *   <li>Walk the sorted list, discarding any match that starts before the
 *       end of the last retained match (overlap removal).</li>
 *   <li>When a heuristic match and an ML match cover the same span, promote
 *       the source to {@link PIIMatch.DetectionSource#HYBRID} and keep the
 *       higher confidence score.</li>
 * </ol>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class CompositeDetector implements PIIDetector {

    private final List<PIIDetector> detectors;

    /**
     * Creates a composite detector that runs each supplied detector in order.
     *
     * @param detectors ordered list of detectors (must not be {@code null})
     */
    public CompositeDetector(List<PIIDetector> detectors) {
        if (detectors == null || detectors.isEmpty()) {
            throw new IllegalArgumentException("At least one detector is required.");
        }
        this.detectors = List.copyOf(detectors);
    }

    @Override
    public String name() { return "CompositeDetector"; }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<PIIMatch> detect(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Collect raw matches from all detectors
        List<PIIMatch> raw = new ArrayList<>();
        for (PIIDetector d : detectors) {
            raw.addAll(d.detect(text));
        }

        if (raw.isEmpty()) return List.of();

        // Sort: by start index asc, then end index desc (longest first)
        Collections.sort(raw);

        // De-duplicate overlapping matches
        List<PIIMatch> deduped = new ArrayList<>();
        int lastEnd = -1;

        for (PIIMatch m : raw) {
            if (m.getStartIndex() < lastEnd) {
                // This match overlaps the previous one.
                // If it's a perfect span match, try to merge source info.
                if (!deduped.isEmpty()) {
                    PIIMatch prev = deduped.get(deduped.size() - 1);
                    if (prev.getStartIndex() == m.getStartIndex()
                            && prev.getEndIndex() == m.getEndIndex()) {
                        // Same span — promote to HYBRID if different sources
                        if (prev.getSource() != m.getSource()
                                && prev.getSource() != PIIMatch.DetectionSource.HYBRID) {
                            deduped.set(deduped.size() - 1, merge(prev, m));
                        }
                    }
                }
                // Either way, skip this match to avoid overlap
                continue;
            }
            deduped.add(m);
            lastEnd = m.getEndIndex();
        }

        return Collections.unmodifiableList(deduped);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** Merges two same-span matches into a HYBRID match with the higher confidence. */
    private PIIMatch merge(PIIMatch a, PIIMatch b) {
        double conf  = Math.max(a.getConfidence(), b.getConfidence());
        // Prefer the more specific type (lower ordinal = higher severity)
        PIIMatch winner = a.getType().getSeverity() >= b.getType().getSeverity() ? a : b;
        return new PIIMatch(
            winner.getType(),
            winner.getValue(),
            winner.getStartIndex(),
            winner.getEndIndex(),
            PIIMatch.DetectionSource.HYBRID,
            conf
        );
    }
}
