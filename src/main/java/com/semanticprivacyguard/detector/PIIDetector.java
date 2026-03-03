package com.semanticprivacyguard.detector;

import com.semanticprivacyguard.model.PIIMatch;

import java.util.List;

/**
 * Contract for all PII detection strategies.
 *
 * <p>Implementations must be <em>thread-safe</em>: a single shared instance
 * is used across all virtual threads in the Project Loom execution model.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public interface PIIDetector {

    /**
     * Scans the supplied {@code text} and returns every PII span detected.
     *
     * <p>Matches may overlap; callers (typically
     * {@link CompositeDetector}) are responsible for de-duplication.</p>
     *
     * @param text the text to scan (never {@code null})
     * @return mutable list of detected spans; empty if none found
     */
    List<PIIMatch> detect(String text);

    /**
     * Returns a short human-readable name used in logging and metrics
     * (e.g. {@code "HeuristicDetector"}, {@code "NaiveBayesMLDetector"}).
     */
    String name();
}
