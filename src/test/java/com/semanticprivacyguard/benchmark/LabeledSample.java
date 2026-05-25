package com.semanticprivacyguard.benchmark;

import java.util.List;

/**
 * An immutable ground-truth sample consisting of a raw text string and its
 * associated list of annotated PII spans.
 *
 * <p>Negative (clean) samples have an empty {@code spans} list.  Positive
 * samples have at least one {@link LabeledSpan} whose character offsets
 * refer to positions within {@code text}.</p>
 *
 * @param text  the raw input text that will be fed to the detector under test
 * @param spans the expected PII spans (empty list for negative/clean samples)
 */
public record LabeledSample(String text, List<LabeledSpan> spans) {

    /**
     * Defensive copy of the span list to preserve immutability.
     */
    public LabeledSample {
        if (text == null) throw new IllegalArgumentException("text must not be null");
        spans = List.copyOf(spans); // unmodifiable defensive copy
    }

    /**
     * Returns {@code true} if this sample contains at least one labeled PII span.
     * Equivalent to {@code !spans().isEmpty()}.
     *
     * @return {@code true} for positive samples, {@code false} for negative ones
     */
    public boolean hasPII() {
        return !spans.isEmpty();
    }
}
