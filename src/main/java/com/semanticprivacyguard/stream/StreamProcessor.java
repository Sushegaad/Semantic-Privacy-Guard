package com.semanticprivacyguard.stream;

import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.detector.CompositeDetector;
import com.semanticprivacyguard.detector.HeuristicDetector;
import com.semanticprivacyguard.detector.MLDetector;
import com.semanticprivacyguard.detector.NLPDetector;
import com.semanticprivacyguard.detector.PIIDetector;
import com.semanticprivacyguard.ml.FeatureExtractor;
import com.semanticprivacyguard.ml.TrainingData;
import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.nlp.NLPModelException;
import com.semanticprivacyguard.nlp.NLPModelLoader;
import com.semanticprivacyguard.tokenizer.PIITokenizer;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * <b>Memory-efficient, line-by-line PII redaction over streams and files.</b>
 *
 * <h2>Why this matters</h2>
 *
 * <p>The standard {@code SemanticPrivacyGuard.redact(String)} API loads the
 * entire input into a single {@code String}.  For a 50 MB log file that means
 * ~50 MB on the heap for the raw text, plus another 100–150 MB for the
 * tokenizer's working strings — 150–200 MB <em>per concurrent request</em>.
 * On a Lambda with 512 MB of RAM handling 10 concurrent calls that is an
 * OOM event waiting to happen.</p>
 *
 * <p>{@code StreamProcessor} processes <em>one line at a time</em>: each line
 * string is detected, redacted, written to the output, and then immediately
 * eligible for garbage collection.  Heap usage stays roughly constant
 * regardless of document size — bounded by the size of the longest single
 * line (typically &lt; 4 KB in log files).</p>
 *
 * <h2>Token numbering</h2>
 *
 * <p>Counters are <em>document-scoped</em>, not line-scoped.  If line 3
 * contains {@code alice@example.com} it gets {@code [EMAIL_1]}; if line 7
 * contains {@code bob@example.com} it gets {@code [EMAIL_2]}.  The same
 * value never produces two identical tokens within the same document.</p>
 *
 * <h2>Quick start</h2>
 *
 * <pre>{@code
 * SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();
 *
 * // File-to-file redaction — constant heap regardless of file size
 * StreamRedactionSummary summary =
 *     spg.redactPath(Path.of("access.log"), Path.of("access.redacted.log"));
 *
 * System.out.println(summary);
 * // → StreamRedactionSummary[lines=84231, linesWithPII=312, matches=389, timeMs=740]
 *
 * // InputStream / OutputStream
 * try (InputStream  in  = new FileInputStream("raw.txt");
 *      OutputStream out = new FileOutputStream("clean.txt")) {
 *     spg.redactStream(in, out);
 * }
 *
 * // Reader / Writer (e.g. inside a servlet filter)
 * spg.redactStream(request.getReader(), response.getWriter());
 *
 * // Lazy Java Stream — integrates with Files.lines() or any Stream<String>
 * try (Stream<String> lines = Files.lines(inputPath)) {
 *     spg.streamProcessor()
 *        .redactLines(lines)
 *        .forEach(System.out::println);
 * }
 * }</pre>
 *
 * <h2>Thread safety</h2>
 *
 * <p>{@code StreamProcessor} instances are <b>thread-safe</b>.  Each call to
 * {@code redact()} or {@code redactLines()} maintains its own independent
 * document-scoped counter state and writes to its own output sink; multiple
 * threads may use the same instance concurrently without coordination.</p>
 *
 * @author Hemant Naik
 * @since 1.1.0
 * @see com.semanticprivacyguard.SemanticPrivacyGuard#streamProcessor()
 */
public final class StreamProcessor {

    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

    private final SPGConfig          config;
    private final CompositeDetector  detector;
    private final PIITokenizer       tokenizer;

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * Creates a {@code StreamProcessor} from the given configuration.
     *
     * <p>The same configuration used to create a
     * {@link com.semanticprivacyguard.SemanticPrivacyGuard} instance may be
     * passed here so that both APIs behave identically.</p>
     *
     * @param config the detection and redaction configuration (never {@code null})
     */
    public StreamProcessor(SPGConfig config) {
        this.config    = Objects.requireNonNull(config, "config must not be null");
        this.detector  = buildDetector(config);
        this.tokenizer = new PIITokenizer(config.getRedactionMode());
    }

    // ── Primary API: Reader / Writer ──────────────────────────────────────────

    /**
     * Reads lines from {@code reader}, redacts PII in each line, and writes
     * the sanitised lines to {@code writer}.
     *
     * <p>Both streams are left open after this call; the caller is responsible
     * for closing them (use try-with-resources).  The writer is flushed but
     * not closed.</p>
     *
     * <p>Line terminators are normalised: each output line is followed by the
     * platform line separator ({@code System.lineSeparator()}).  The final
     * line does <em>not</em> receive a trailing newline if the source did not
     * have one — the implementation uses a write-before pattern.</p>
     *
     * @param reader the source of lines (never {@code null})
     * @param writer the redaction output sink (never {@code null})
     * @return aggregated {@link StreamRedactionSummary} for the full document
     * @throws IOException if any I/O error occurs
     */
    public StreamRedactionSummary redact(Reader reader, Writer writer) throws IOException {
        Objects.requireNonNull(reader, "reader must not be null");
        Objects.requireNonNull(writer, "writer must not be null");

        BufferedReader br = (reader instanceof BufferedReader b)
                          ? b : new BufferedReader(reader);
        BufferedWriter bw = (writer instanceof BufferedWriter b)
                          ? b : new BufferedWriter(writer);

        // Document-scoped state — one shared counter map per document so
        // [EMAIL_1], [EMAIL_2] are globally unique within this stream pass.
        Map<PIIType, Integer> globalCounters   = new EnumMap<>(PIIType.class);
        Map<PIIType, Long>    matchCountByType = new EnumMap<>(PIIType.class);

        long totalLines   = 0;
        long linesWithPII = 0;
        long totalMatches = 0;
        int  minSev       = config.getMinimumSeverity();
        long start        = System.currentTimeMillis();

        // write-before pattern: newline is prepended to every line except the
        // first, so the last line has no trailing newline iff the source didn't.
        boolean firstLine = true;
        String  rawLine;

        while ((rawLine = br.readLine()) != null) {
            if (!firstLine) bw.newLine();
            firstLine = false;
            totalLines++;

            // ── Detect ───────────────────────────────────────────────────────
            List<PIIMatch> allMatches = detector.detect(rawLine);
            List<PIIMatch> filtered   = new ArrayList<>(allMatches.size());
            for (PIIMatch m : allMatches) {
                if (m.getType().getSeverity() >= minSev) filtered.add(m);
            }

            // ── Accumulate stats ─────────────────────────────────────────────
            if (!filtered.isEmpty()) {
                linesWithPII++;
                totalMatches += filtered.size();
                for (PIIMatch m : filtered) {
                    matchCountByType.merge(m.getType(), 1L, Long::sum);
                }
            }

            // ── Redact with shared counters, write immediately ────────────────
            PIITokenizer.RedactionOutput output =
                tokenizer.redact(rawLine, filtered, globalCounters);
            bw.write(output.redactedText());
        }

        bw.flush();

        return new StreamRedactionSummary(
            totalLines, linesWithPII, totalMatches,
            System.currentTimeMillis() - start,
            matchCountByType);
    }

    // ── InputStream / OutputStream ────────────────────────────────────────────

    /**
     * Byte-stream variant of {@link #redact(Reader, Writer)}.
     * Reads from {@code in} and writes to {@code out} using the given charset.
     *
     * @param in      source byte stream (never {@code null})
     * @param out     output byte stream (never {@code null})
     * @param charset character encoding to use (never {@code null})
     * @return aggregated {@link StreamRedactionSummary}
     * @throws IOException if any I/O error occurs
     */
    public StreamRedactionSummary redact(InputStream in,
                                         OutputStream out,
                                         Charset charset) throws IOException {
        Objects.requireNonNull(in,      "in must not be null");
        Objects.requireNonNull(out,     "out must not be null");
        Objects.requireNonNull(charset, "charset must not be null");
        return redact(
            new InputStreamReader(in,  charset),
            new OutputStreamWriter(out, charset));
    }

    /**
     * Byte-stream variant using UTF-8 encoding.
     *
     * @param in  source byte stream (never {@code null})
     * @param out output byte stream (never {@code null})
     * @return aggregated {@link StreamRedactionSummary}
     * @throws IOException if any I/O error occurs
     */
    public StreamRedactionSummary redact(InputStream in,
                                         OutputStream out) throws IOException {
        return redact(in, out, DEFAULT_CHARSET);
    }

    // ── Path convenience ──────────────────────────────────────────────────────

    /**
     * File-to-file redaction.  Reads from {@code inputFile}, writes the
     * redacted output to {@code outputFile} (which is created or overwritten),
     * and returns aggregated statistics.
     *
     * <p>Both files are handled with try-with-resources so they are always
     * closed, even on error.</p>
     *
     * <pre>{@code
     * StreamRedactionSummary s =
     *     processor.redact(Path.of("server.log"), Path.of("server.clean.log"));
     * System.out.println(s);
     * }</pre>
     *
     * @param inputFile  path to the source file (must exist and be readable)
     * @param outputFile path to the destination file (created if absent)
     * @return aggregated {@link StreamRedactionSummary}
     * @throws IOException if either file cannot be opened or an I/O error occurs
     */
    public StreamRedactionSummary redact(Path inputFile,
                                         Path outputFile) throws IOException {
        Objects.requireNonNull(inputFile,  "inputFile must not be null");
        Objects.requireNonNull(outputFile, "outputFile must not be null");
        try (BufferedReader reader = Files.newBufferedReader(inputFile,  DEFAULT_CHARSET);
             BufferedWriter writer = Files.newBufferedWriter(outputFile, DEFAULT_CHARSET)) {
            return redact(reader, writer);
        }
    }

    // ── Stream<String> API ────────────────────────────────────────────────────

    /**
     * Returns a lazy {@link Stream}{@code <String>} where each element is the
     * redacted version of the corresponding input line.
     *
     * <p>Token numbering is document-scoped: counters are shared across the
     * entire stream, so each value receives a globally unique token
     * ({@code [EMAIL_1]}, {@code [EMAIL_2]}, …) even when PII appears on
     * different lines.</p>
     *
     * <p><b>Parallel streams are not supported.</b>  Document-scoped counters
     * require sequential processing to guarantee deterministic token numbers.
     * If a parallel stream is passed this method throws
     * {@link IllegalArgumentException}.  Call {@code stream.sequential()}
     * before passing if needed.</p>
     *
     * <p>This method integrates naturally with {@link Files#lines}:</p>
     * <pre>{@code
     * try (Stream<String> lines = Files.lines(inputPath)) {
     *     processor.redactLines(lines)
     *              .forEach(outputWriter::println);
     * }
     * }</pre>
     *
     * <p>Note: unlike {@link #redact(Reader, Writer)}, this method does not
     * return a {@link StreamRedactionSummary}; use the Reader/Writer or Path
     * API when you need statistics.</p>
     *
     * @param lines a <em>sequential</em> stream of input lines (never {@code null})
     * @return a lazy stream of redacted lines in the same order
     * @throws IllegalArgumentException if {@code lines.isParallel()} is {@code true}
     */
    public Stream<String> redactLines(Stream<String> lines) {
        Objects.requireNonNull(lines, "lines must not be null");
        if (lines.isParallel()) {
            throw new IllegalArgumentException(
                "redactLines() requires a sequential stream — call lines.sequential() first. "
              + "Parallel processing would produce non-deterministic token numbers.");
        }

        // Mutable state captured in the lambda closure — safe because the
        // stream is guaranteed sequential by the check above.
        Map<PIIType, Integer> globalCounters = new EnumMap<>(PIIType.class);
        int minSev = config.getMinimumSeverity();

        return lines.map(line -> {
            if (line == null) return "";

            List<PIIMatch> allMatches = detector.detect(line);
            List<PIIMatch> filtered   = new ArrayList<>(allMatches.size());
            for (PIIMatch m : allMatches) {
                if (m.getType().getSeverity() >= minSev) filtered.add(m);
            }
            return tokenizer.redact(line, filtered, globalCounters).redactedText();
        });
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private static CompositeDetector buildDetector(SPGConfig cfg) {
        List<PIIDetector> detectors = new ArrayList<>();

        if (cfg.isHeuristicEnabled()) {
            detectors.add(new HeuristicDetector(cfg.getEnabledTypes()));
        }
        if (cfg.isMlEnabled()) {
            detectors.add(new MLDetector(
                TrainingData.buildDefaultClassifier(),
                new FeatureExtractor(),
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
