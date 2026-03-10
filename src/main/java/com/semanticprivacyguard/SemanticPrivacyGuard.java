package com.semanticprivacyguard;

import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.detector.CompositeDetector;
import com.semanticprivacyguard.detector.HeuristicDetector;
import com.semanticprivacyguard.detector.MLDetector;
import com.semanticprivacyguard.detector.NLPDetector;
import com.semanticprivacyguard.detector.PIIDetector;
import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.RedactionResult;
import com.semanticprivacyguard.nlp.NLPModelException;
import com.semanticprivacyguard.nlp.NLPModelLoader;
import com.semanticprivacyguard.stream.StreamProcessor;
import com.semanticprivacyguard.stream.StreamRedactionSummary;
import com.semanticprivacyguard.tokenizer.PIITokenizer;
import com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionOutput;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * <b>Semantic Privacy Guard — primary public API.</b>
 *
 * <p>Acts as a lightweight, high-performance AI privacy firewall that
 * intercepts text, identifies PII using a hybrid heuristic + Naive Bayes ML
 * approach, and redacts it before it leaves the corporate network.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // 1. Create with defaults
 * SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();
 *
 * // 2. Redact
 * RedactionResult result = spg.redact(
 *     "Please email John Doe at john.doe@acme.com or call (555) 123-4567. " +
 *     "His SSN is 123-45-6789."
 * );
 *
 * System.out.println(result.getRedactedText());
 * // → "Please email [PERSON_NAME_1] [PERSON_NAME_2] at [EMAIL_1] or call [PHONE_1].
 * //    His SSN is [SSN_1]."
 *
 * System.out.println(result.getMatchCount()); // → 5
 * }</pre>
 *
 * <h2>Custom configuration</h2>
 * <pre>{@code
 * SPGConfig config = SPGConfig.builder()
 *     .redactionMode(RedactionMode.MASK)
 *     .mlConfidenceThreshold(0.75)
 *     .minimumSeverity(6)
 *     .build();
 *
 * SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);
 * }</pre>
 *
 * <h2>Stream / large-file processing</h2>
 * <pre>{@code
 * // Constant heap usage regardless of file size — one line at a time
 * StreamRedactionSummary s =
 *     spg.redactPath(Path.of("server.log"), Path.of("server.clean.log"));
 *
 * System.out.println(s);
 * // → StreamRedactionSummary[lines=84231, linesWithPII=312, matches=389, timeMs=740]
 * }</pre>
 *
 * <h2>Thread safety</h2>
 *
 * <p>All instances are <b>thread-safe</b> and designed for use with Java 17+
 * virtual threads (Project Loom).  A single shared instance can safely handle
 * millions of concurrent redaction calls with negligible overhead.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class SemanticPrivacyGuard {

    /** Library version, aligned with pom.xml. */
    public static final String VERSION = "1.1.0";

    private final SPGConfig         config;
    private final CompositeDetector detector;
    private final PIITokenizer      tokenizer;

    // ── Construction ──────────────────────────────────────────────────────────

    private SemanticPrivacyGuard(SPGConfig config) {
        this.config    = config;
        this.detector  = buildDetector(config);
        this.tokenizer = new PIITokenizer(config.getRedactionMode());
    }

    /**
     * Creates a new instance with default configuration.
     *
     * @return a ready-to-use {@code SemanticPrivacyGuard}
     */
    public static SemanticPrivacyGuard create() {
        return new SemanticPrivacyGuard(SPGConfig.defaults());
    }

    /**
     * Creates a new instance with the supplied configuration.
     *
     * @param config custom configuration (never {@code null})
     * @return a ready-to-use {@code SemanticPrivacyGuard}
     */
    public static SemanticPrivacyGuard create(SPGConfig config) {
        return new SemanticPrivacyGuard(
            java.util.Objects.requireNonNull(config, "config must not be null"));
    }

    // ── Core API ──────────────────────────────────────────────────────────────

    /**
     * Scans {@code text} for PII and returns a full {@link RedactionResult}
     * containing the sanitised text, all detected matches, and (optionally) a
     * reverse-lookup map.
     *
     * @param text the raw input text (may be {@code null} or blank)
     * @return a {@link RedactionResult}; if the input is {@code null} or blank,
     *         returns a result wrapping the original text with no matches
     */
    public RedactionResult redact(String text) {
        if (text == null || text.isBlank()) {
            return new RedactionResult(
                text == null ? "" : text,
                text == null ? "" : text,
                Collections.emptyList(),
                Collections.emptyMap(),
                0L);
        }

        long start = System.currentTimeMillis();

        // Detect
        List<PIIMatch> allMatches = detector.detect(text);

        // Apply minimum severity filter
        int minSev = config.getMinimumSeverity();
        List<PIIMatch> filtered = new ArrayList<>();
        for (PIIMatch m : allMatches) {
            if (m.getType().getSeverity() >= minSev) filtered.add(m);
        }

        // Tokenize / redact
        RedactionOutput output = tokenizer.redact(text, filtered);

        long elapsed = System.currentTimeMillis() - start;

        return new RedactionResult(
            text,
            output.redactedText(),
            filtered,
            config.isBuildReverseMap()
                ? output.reverseMap()
                : Collections.emptyMap(),
            elapsed
        );
    }

    /**
     * Convenience method: scans {@code text} and returns {@code true} if any
     * PII above the configured minimum severity is detected.
     *
     * <p>This is faster than {@link #redact} when you only need a yes/no
     * answer (e.g. a pre-flight check before logging).</p>
     *
     * @param text the text to scan
     * @return {@code true} if PII was found
     */
    public boolean containsPII(String text) {
        if (text == null || text.isBlank()) return false;
        int minSev = config.getMinimumSeverity();
        return detector.detect(text).stream()
            .anyMatch(m -> m.getType().getSeverity() >= minSev);
    }

    /**
     * Returns only the detected matches without applying redaction.
     * Useful for audit / reporting pipelines where the original text
     * must be preserved but a list of findings is required.
     *
     * @param text the text to analyse
     * @return unmodifiable list of detected PII matches
     */
    public List<PIIMatch> analyse(String text) {
        if (text == null || text.isBlank()) return List.of();
        int minSev = config.getMinimumSeverity();
        return detector.detect(text).stream()
            .filter(m -> m.getType().getSeverity() >= minSev)
            .toList();
    }

    /**
     * Returns the active configuration for this instance.
     */
    public SPGConfig getConfig() { return config; }

    // ── Stream API ────────────────────────────────────────────────────────────

    /**
     * Returns a {@link StreamProcessor} configured identically to this instance.
     *
     * <p>Use the returned processor when you need to redact large files or
     * byte/character streams without loading the entire content into memory.
     * Processing is line-by-line with constant heap usage regardless of
     * document size.</p>
     *
     * <pre>{@code
     * StreamProcessor proc = spg.streamProcessor();
     *
     * // Redact a 500 MB log file with a small, stable heap footprint
     * StreamRedactionSummary s =
     *     proc.redact(Path.of("big.log"), Path.of("big.clean.log"));
     * }</pre>
     *
     * @return a new {@link StreamProcessor} sharing this instance's configuration
     */
    public StreamProcessor streamProcessor() {
        return new StreamProcessor(config);
    }

    /**
     * Convenience method: redacts an {@link InputStream} line-by-line and writes
     * the sanitised output to {@code out}, both streams treated as UTF-8.
     *
     * <p>Both streams are left open; the caller is responsible for closing them.
     * Equivalent to {@code streamProcessor().redact(in, out)}.</p>
     *
     * @param in  the source byte stream (never {@code null})
     * @param out the output byte stream (never {@code null})
     * @return aggregated {@link StreamRedactionSummary} for the full document
     * @throws IOException if any I/O error occurs
     */
    public StreamRedactionSummary redactStream(InputStream in,
                                               OutputStream out) throws IOException {
        return streamProcessor().redact(in, out);
    }

    /**
     * Convenience method: redacts a character stream line-by-line.
     *
     * <p>Both streams are left open; the caller is responsible for closing them.
     * Equivalent to {@code streamProcessor().redact(reader, writer)}.</p>
     *
     * @param reader the source character stream (never {@code null})
     * @param writer the output character stream (never {@code null})
     * @return aggregated {@link StreamRedactionSummary} for the full document
     * @throws IOException if any I/O error occurs
     */
    public StreamRedactionSummary redactStream(Reader reader,
                                               Writer writer) throws IOException {
        return streamProcessor().redact(reader, writer);
    }

    /**
     * Convenience method: redacts a file on disk and writes the result to another
     * file, processing line-by-line with constant heap usage.
     *
     * <p>Equivalent to {@code streamProcessor().redact(inputFile, outputFile)}.</p>
     *
     * @param inputFile  path of the file to redact (must be readable)
     * @param outputFile path of the output file (created or overwritten)
     * @return aggregated {@link StreamRedactionSummary} for the full document
     * @throws IOException if either file cannot be opened or an I/O error occurs
     */
    public StreamRedactionSummary redactPath(Path inputFile,
                                             Path outputFile) throws IOException {
        return streamProcessor().redact(inputFile, outputFile);
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static CompositeDetector buildDetector(SPGConfig cfg) {
        List<PIIDetector> detectors = new ArrayList<>();

        if (cfg.isHeuristicEnabled()) {
            detectors.add(new HeuristicDetector(cfg.getEnabledTypes()));
        }
        if (cfg.isMlEnabled()) {
            detectors.add(new MLDetector(
                com.semanticprivacyguard.ml.TrainingData.buildDefaultClassifier(),
                new com.semanticprivacyguard.ml.FeatureExtractor(),
                cfg.getMlConfidenceThreshold()
            ));
        }
        if (cfg.isNlpEnabled()) {
            try {
                NLPModelLoader.LoadedModels models =
                    (cfg.getNlpModelsDirectory() != null)
                        ? NLPModelLoader.fromDirectory(cfg.getNlpModelsDirectory())
                        : NLPModelLoader.fromClasspath();
                detectors.add(new NLPDetector(models, cfg.getNlpConfidenceThreshold()));
            } catch (NLPModelException e) {
                throw new IllegalStateException(
                    "NLP is enabled but models could not be loaded — "
                  + e.getMessage()
                  + ". See README.md#nlp-setup for model download instructions.",
                    e);
            }
        }

        return new CompositeDetector(detectors);
    }
}
