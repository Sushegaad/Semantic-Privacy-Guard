package com.semanticprivacyguard.benchmark;

import com.semanticprivacyguard.model.PIIType;

/**
 * Precision / recall / F1 metrics for a single {@link PIIType}.
 *
 * <p>Definitions (using overlap-based matching so partial detections count):</p>
 * <ul>
 *   <li><b>TP</b> — a labeled span that was covered by at least one detection of
 *       the same type.</li>
 *   <li><b>FP</b> — a detection that does not overlap any labeled span of the
 *       same type.</li>
 *   <li><b>FN</b> — a labeled span that no detection of the same type covered.</li>
 * </ul>
 *
 * <p>All three boundary cases (tp+fp == 0, tp+fn == 0, p+r == 0) return the
 * mathematically neutral value rather than throwing, so callers can safely
 * aggregate across sparse types.</p>
 *
 * @param type the PII category these metrics apply to
 * @param tp   true positive count
 * @param fp   false positive count
 * @param fn   false negative count
 */
public record TypeMetrics(PIIType type, int tp, int fp, int fn) {

    /** Validates that counts are non-negative. */
    public TypeMetrics {
        if (tp < 0) throw new IllegalArgumentException("tp must be >= 0");
        if (fp < 0) throw new IllegalArgumentException("fp must be >= 0");
        if (fn < 0) throw new IllegalArgumentException("fn must be >= 0");
    }

    /**
     * Returns the precision: {@code tp / (tp + fp)}.
     *
     * <p>Returns {@code 1.0} when there are no detections at all ({@code tp + fp == 0})
     * — treating "no detections" as perfectly precise (no false positives) is the
     * convention used by scikit-learn's {@code zero_division=1} option.</p>
     *
     * @return precision in {@code [0.0, 1.0]}
     */
    public double precision() {
        int denom = tp + fp;
        return denom == 0 ? 1.0 : (double) tp / denom;
    }

    /**
     * Returns the recall: {@code tp / (tp + fn)}.
     *
     * <p>Returns {@code 1.0} when there are no labeled spans for this type
     * ({@code tp + fn == 0}) — consistent with treating an empty reference as
     * trivially recalled.</p>
     *
     * @return recall in {@code [0.0, 1.0]}
     */
    public double recall() {
        int denom = tp + fn;
        return denom == 0 ? 1.0 : (double) tp / denom;
    }

    /**
     * Returns the F1 score — the harmonic mean of {@link #precision()} and
     * {@link #recall()}: {@code 2 * p * r / (p + r)}.
     *
     * <p>Returns {@code 0.0} when both precision and recall are zero.</p>
     *
     * @return F1 score in {@code [0.0, 1.0]}
     */
    public double f1() {
        double p = precision();
        double r = recall();
        double sum = p + r;
        return sum == 0.0 ? 0.0 : 2.0 * p * r / sum;
    }

    @Override
    public String toString() {
        return String.format("TypeMetrics{type=%s, tp=%d, fp=%d, fn=%d, P=%.3f, R=%.3f, F1=%.3f}",
                type, tp, fp, fn, precision(), recall(), f1());
    }
}
