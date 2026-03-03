package com.semanticprivacyguard.config;

import com.semanticprivacyguard.detector.MLDetector;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionMode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration for a {@code SemanticPrivacyGuard} instance.
 *
 * <p>Use the nested {@link Builder} to construct instances:</p>
 * <pre>{@code
 * SPGConfig config = SPGConfig.builder()
 *     .redactionMode(RedactionMode.TOKEN)
 *     .mlConfidenceThreshold(0.70)
 *     .enabledTypes(Set.of(PIIType.EMAIL, PIIType.SSN, PIIType.CREDIT_CARD))
 *     .minimumSeverity(6)
 *     .buildReverseMap(true)
 *     .build();
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class SPGConfig {

    // ── Defaults ──────────────────────────────────────────────────────────────

    public static final RedactionMode DEFAULT_REDACTION_MODE   = RedactionMode.TOKEN;
    public static final double        DEFAULT_ML_THRESHOLD     = MLDetector.DEFAULT_CONFIDENCE_THRESHOLD;
    public static final int           DEFAULT_MIN_SEVERITY     = 1;
    public static final boolean       DEFAULT_BUILD_REVERSE_MAP= true;
    public static final boolean       DEFAULT_HEURISTIC_ENABLED= true;
    public static final boolean       DEFAULT_ML_ENABLED       = true;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final RedactionMode  redactionMode;
    private final double         mlConfidenceThreshold;
    private final Set<PIIType>   enabledTypes;          // empty = all types
    private final int            minimumSeverity;
    private final boolean        buildReverseMap;
    private final boolean        heuristicEnabled;
    private final boolean        mlEnabled;

    private SPGConfig(Builder b) {
        this.redactionMode          = b.redactionMode;
        this.mlConfidenceThreshold  = b.mlConfidenceThreshold;
        this.enabledTypes           = b.enabledTypes.isEmpty()
                                      ? Collections.emptySet()
                                      : Collections.unmodifiableSet(
                                            EnumSet.copyOf(b.enabledTypes));
        this.minimumSeverity        = b.minimumSeverity;
        this.buildReverseMap        = b.buildReverseMap;
        this.heuristicEnabled       = b.heuristicEnabled;
        this.mlEnabled              = b.mlEnabled;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public RedactionMode getRedactionMode()         { return redactionMode;         }
    public double        getMlConfidenceThreshold() { return mlConfidenceThreshold; }
    /** Returns the set of enabled types; empty set means all types are enabled. */
    public Set<PIIType>  getEnabledTypes()          { return enabledTypes;          }
    public int           getMinimumSeverity()       { return minimumSeverity;       }
    public boolean       isBuildReverseMap()        { return buildReverseMap;       }
    public boolean       isHeuristicEnabled()       { return heuristicEnabled;      }
    public boolean       isMlEnabled()              { return mlEnabled;             }

    // ── Factory ───────────────────────────────────────────────────────────────

    /** Returns a builder pre-loaded with default values. */
    public static Builder builder() { return new Builder(); }

    /** Returns a default configuration instance. */
    public static SPGConfig defaults() { return builder().build(); }

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fluent builder for {@link SPGConfig}.
     */
    public static final class Builder {

        private RedactionMode redactionMode         = DEFAULT_REDACTION_MODE;
        private double        mlConfidenceThreshold = DEFAULT_ML_THRESHOLD;
        private Set<PIIType>  enabledTypes          = EnumSet.noneOf(PIIType.class);
        private int           minimumSeverity       = DEFAULT_MIN_SEVERITY;
        private boolean       buildReverseMap       = DEFAULT_BUILD_REVERSE_MAP;
        private boolean       heuristicEnabled      = DEFAULT_HEURISTIC_ENABLED;
        private boolean       mlEnabled             = DEFAULT_ML_ENABLED;

        private Builder() {}

        /**
         * Sets the output redaction mode.
         *
         * @param mode {@link RedactionMode#TOKEN} (default), {@code MASK},
         *             or {@code BLANK}
         */
        public Builder redactionMode(RedactionMode mode) {
            this.redactionMode = Objects.requireNonNull(mode);
            return this;
        }

        /**
         * Sets the Naive Bayes posterior probability threshold for the ML layer.
         * Lower values increase recall; higher values increase precision.
         * Default: {@value DEFAULT_ML_THRESHOLD}.
         *
         * @param threshold value in (0.0, 1.0]
         */
        public Builder mlConfidenceThreshold(double threshold) {
            if (threshold <= 0.0 || threshold > 1.0) throw new IllegalArgumentException(
                "threshold must be in (0.0, 1.0]");
            this.mlConfidenceThreshold = threshold;
            return this;
        }

        /**
         * Restricts detection to the specified PII types.
         * Passing an empty set (or calling this method with no arguments)
         * enables all types.
         *
         * @param types the subset of types to detect
         */
        public Builder enabledTypes(Set<PIIType> types) {
            this.enabledTypes = types == null ? EnumSet.noneOf(PIIType.class)
                                              : EnumSet.copyOf(types);
            return this;
        }

        /**
         * Sets the minimum severity score ({@code 1–10}) a match must have to
         * be included in results. Useful for high-throughput paths that should
         * only care about the most sensitive data (e.g. {@code >= 8} for
         * SSN/credit-card/API-key only).
         *
         * @param severity minimum severity; default is {@value DEFAULT_MIN_SEVERITY}
         */
        public Builder minimumSeverity(int severity) {
            if (severity < 1 || severity > 10) throw new IllegalArgumentException(
                "severity must be between 1 and 10");
            this.minimumSeverity = severity;
            return this;
        }

        /**
         * Controls whether the reverse token-to-original map is populated.
         * Disable if you never need to de-tokenise for a minor performance gain.
         *
         * @param build {@code true} to populate the map (default)
         */
        public Builder buildReverseMap(boolean build) {
            this.buildReverseMap = build;
            return this;
        }

        /**
         * Enables or disables the heuristic (regex) detection layer.
         * Disabling is useful for benchmarking or if you only want ML results.
         */
        public Builder heuristicEnabled(boolean enabled) {
            this.heuristicEnabled = enabled;
            return this;
        }

        /**
         * Enables or disables the ML (Naive Bayes) detection layer.
         * Disabling is useful for ultra-low-latency paths where only obvious
         * structural PII needs to be caught.
         */
        public Builder mlEnabled(boolean enabled) {
            this.mlEnabled = enabled;
            return this;
        }

        /** Builds and returns an immutable {@link SPGConfig}. */
        public SPGConfig build() {
            if (!heuristicEnabled && !mlEnabled) {
                throw new IllegalStateException(
                    "At least one of heuristicEnabled or mlEnabled must be true.");
            }
            return new SPGConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
            "SPGConfig{mode=%s, mlThreshold=%.2f, enabledTypes=%s, "
          + "minSeverity=%d, reverseMap=%b, heuristic=%b, ml=%b}",
            redactionMode, mlConfidenceThreshold,
            enabledTypes.isEmpty() ? "ALL" : enabledTypes,
            minimumSeverity, buildReverseMap,
            heuristicEnabled, mlEnabled);
    }
}
