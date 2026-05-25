package com.semanticprivacyguard.benchmark;

import com.semanticprivacyguard.model.PIIType;

/**
 * An immutable ground-truth annotation for a single PII span within a text
 * sample.
 *
 * <p>Start is inclusive; end is exclusive — consistent with Java's
 * {@link String#substring(int, int)} convention and with
 * {@link com.semanticprivacyguard.model.PIIMatch} semantics.</p>
 *
 * @param type  the expected PII category at this span
 * @param start inclusive start offset (character index into the sample text)
 * @param end   exclusive end offset
 */
public record LabeledSpan(PIIType type, int start, int end) {

    /**
     * Constructs a {@code LabeledSpan} with basic validation.
     *
     * @throws IllegalArgumentException if {@code start} or {@code end} are negative,
     *                                  or if {@code end < start}
     */
    public LabeledSpan {
        if (start < 0) throw new IllegalArgumentException("start must be >= 0, got: " + start);
        if (end < start) throw new IllegalArgumentException(
                "end must be >= start, got: start=" + start + " end=" + end);
    }

    /**
     * Returns {@code true} if the interval {@code [s, e)} shares at least one
     * character with this span's interval {@code [start, end)}.
     *
     * <p>Two spans overlap when neither is entirely before or after the other:</p>
     * <pre>
     *   overlap ⟺ s &lt; end &amp;&amp; e &gt; start
     * </pre>
     *
     * @param s inclusive start of the interval to test
     * @param e exclusive end of the interval to test
     * @return {@code true} if the intervals share any characters
     */
    public boolean overlaps(int s, int e) {
        return s < end && e > start;
    }
}
