package com.semanticprivacyguard;

import com.semanticprivacyguard.benchmark.BenchmarkResult;
import com.semanticprivacyguard.benchmark.LabeledSample;
import com.semanticprivacyguard.benchmark.ReportGenerator;
import com.semanticprivacyguard.benchmark.SyntheticDataset;
import com.semanticprivacyguard.benchmark.TypeMetrics;
import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.model.PIIType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Benchmark suite for Semantic Privacy Guard.
 *
 * <p>Run with: {@code mvn test -P benchmark}</p>
 *
 * <h2>Test methods</h2>
 * <ol>
 *   <li><b>{@link #fullBenchmark()}</b> — precision / recall / F1 + throughput
 *       using a fully labeled synthetic dataset ({@link SyntheticDataset}).
 *       Writes machine-readable results to {@code docs/benchmark-results.json}.
 *       CI gate: macro F1 of the full pipeline must be &ge; 0.75.</li>
 *   <li><b>{@link #naiveVsSpgThroughputAndFalsePositives()}</b> — backwards-compatible
 *       throughput / false-positive comparison inherited from the original
 *       {@code BenchmarkTest}.</li>
 * </ol>
 *
 * <h2>No JMH dependency</h2>
 * <p>Both tests use plain {@link System#currentTimeMillis()} timing rather than
 * JMH to keep the dependency count at zero.  The numbers are sufficient for
 * order-of-magnitude comparisons on commodity hardware.</p>
 */
@DisplayName("Benchmark: precision/recall/F1 + throughput")
class BenchmarkTest {

    // =========================================================================
    // Test 1 — Full precision / recall / F1 benchmark
    // =========================================================================

    /**
     * Runs the labeled dataset through two configurations and reports accuracy +
     * throughput.  Writes {@code docs/benchmark-results.json}.
     *
     * <p>CI gate: the full (Heuristic+ML) pipeline must achieve macro F1 &ge; 0.75
     * to pass.</p>
     */
    @Test
    @DisplayName("Full benchmark: precision, recall, F1, throughput")
    void fullBenchmark() {
        List<LabeledSample> dataset = SyntheticDataset.generate();

        // Config 1: Naive regex baseline — heuristic only, no ML
        SPGConfig naiveCfg = SPGConfig.builder().mlEnabled(false).build();
        BenchmarkResult naive = BenchmarkResult.compute(
                "SPG Heuristic-only", dataset, SemanticPrivacyGuard.create(naiveCfg));

        // Config 2: Full pipeline — heuristic + ML (default)
        BenchmarkResult full = BenchmarkResult.compute(
                "SPG Heuristic+ML", dataset, SemanticPrivacyGuard.create());

        // Print summary table to stdout
        printResults(naive, full);

        // Persist machine-readable JSON to docs/benchmark-results.json
        ReportGenerator.writeJson(List.of(naive, full));

        // CI gate: the full pipeline must meet the minimum F1 threshold
        assertTrue(full.macroF1() >= 0.75,
                "SPG Heuristic+ML macro F1 should be >= 0.75 but was: "
                        + String.format("%.4f", full.macroF1()));
    }

    // =========================================================================
    // Test 2 — Backwards-compatible throughput / false-positive test
    // =========================================================================

    /**
     * Compares throughput and false-positive count of three approaches over a
     * small fixed corpus.
     *
     * <p>Retained from the original {@code BenchmarkTest} so that existing CI
     * pipelines referencing this test by display name continue to pass.</p>
     */
    @Test
    @DisplayName("Benchmark: naive regex vs SPG")
    void naiveVsSpgThroughputAndFalsePositives() {
        // Warm up JIT
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            runNaiveRegex();
            runSPGHeuristicOnly();
            runSPGFull();
        }

        // Measure
        long naiveMs = timeMs(BenchmarkTest::runNaiveRegex,       MEASURE_ROUNDS);
        long heurMs  = timeMs(BenchmarkTest::runSPGHeuristicOnly, MEASURE_ROUNDS);
        long fullMs  = timeMs(BenchmarkTest::runSPGFull,          MEASURE_ROUNDS);

        // Accuracy (false positive count on clean sentences)
        int naiveFP = countFalsePositivesNaive();
        int heurFP  = countFalsePositivesHeuristic();
        int fullFP  = countFalsePositivesFull();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              SPG Benchmark Results (" + MEASURE_ROUNDS + " rounds)              ║");
        System.out.println("╠═══════════════════════════╦════════════╦════════════════════╣");
        System.out.println("║ Approach                  ║ Time (ms)  ║ False Positives    ║");
        System.out.println("╠═══════════════════════════╬════════════╬════════════════════╣");
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "Naive Regex",        naiveMs, naiveFP);
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "SPG Heuristic-only", heurMs,  heurFP);
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "SPG Full (H+ML)",    fullMs,  fullFP);
        System.out.println("╚═══════════════════════════╩════════════╩════════════════════╝");
        System.out.printf ("%nML disambiguation reduces false positives by %d (%.0f%% improvement)%n",
                (naiveFP - fullFP),
                naiveFP > 0 ? 100.0 * (naiveFP - fullFP) / naiveFP : 0.0);

        // Assert SPG is at least as accurate as naive regex
        assertTrue(fullFP <= naiveFP,
                "SPG should not produce more false positives than naive regex");
    }

    // =========================================================================
    // Print helper
    // =========================================================================

    /**
     * Prints a formatted summary table for all supplied {@link BenchmarkResult}
     * instances plus per-type breakdowns.
     */
    private static void printResults(BenchmarkResult... results) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        Semantic Privacy Guard — Benchmark Results                ║");
        System.out.println("╠═══════════════════════╦═══════════╦══════════╦═══════╦════════════════╦═════════╣");
        System.out.println("║ Config                ║ Precision ║ Recall   ║ F1    ║ Throughput MB/s║ Heap MB ║");
        System.out.println("╠═══════════════════════╬═══════════╬══════════╬═══════╬════════════════╬═════════╣");
        for (BenchmarkResult r : results) {
            System.out.printf(
                "║ %-21s ║ %9.3f ║ %8.3f ║ %5.3f ║ %14.1f ║ %7d ║%n",
                r.configName(),
                r.macroPrecision(),
                r.macroRecall(),
                r.macroF1(),
                r.throughputMbps(),
                r.peakHeapMb());
        }
        System.out.println("╚═══════════════════════╩═══════════╩══════════╩═══════╩════════════════╩═════════╝");

        // Per-type breakdown for each config
        for (BenchmarkResult r : results) {
            System.out.println("\n  Per-type breakdown — " + r.configName());
            System.out.println("  ┌────────────────────┬──────┬──────┬──────┬───────────┬────────┬───────┐");
            System.out.println("  │ Type               │  TP  │  FP  │  FN  │ Precision │ Recall │  F1   │");
            System.out.println("  ├────────────────────┼──────┼──────┼──────┼───────────┼────────┼───────┤");

            Map<PIIType, TypeMetrics> perType = r.perType();
            // Print in enum declaration order for consistency
            for (PIIType type : PIIType.values()) {
                TypeMetrics m = perType.get(type);
                if (m == null) continue;
                System.out.printf(
                    "  │ %-18s │ %4d │ %4d │ %4d │ %9.3f │ %6.3f │ %5.3f │%n",
                    type.name(), m.tp(), m.fp(), m.fn(),
                    m.precision(), m.recall(), m.f1());
            }
            System.out.println("  └────────────────────┴──────┴──────┴──────┴───────────┴────────┴───────┘");
        }
        System.out.println();
    }

    // =========================================================================
    // Backwards-compat benchmark corpus (Test 2)
    // =========================================================================

    private static final String[] CORPUS = {
        // True positives
        "Dear Alice Johnson, your SSN is 234-56-7890 and email is alice.j@corp.com",
        "Call support at (800) 555-2368 or email help@example.org",
        "Password: S3cr3tP@ss! and API key: sk-abcdefghijklmnopqrstuvwxyz1234",
        "Patient ID: 234-56-7890, dob: 03/15/1985, referred by Dr. Smith",
        "Transfer to GB29NWBK60161331926819 from account routing=021000021",
        "AWS_ACCESS_KEY_ID=AKIAIOSFODNN7EXAMPLE in production config",
        "Server 10.0.0.1 also known as 192.168.100.50 behind the firewall",
        "Card on file: 4532 0151 1283 0366 — Visa ending in 0366",

        // True negatives — should NOT be flagged
        "I ate an apple and watched an Apple keynote on YouTube.",
        "The quick brown fox jumps over the lazy dog.",
        "Version 2024.01.15 of the library was released.",
        "The server returned error code 404 after 3 retries.",
        "Pi is approximately 3.14159265358979, a famous constant.",
        "In the book, John meets Mary at the marketplace in chapter 5.",
    };

    private static final int WARMUP_ROUNDS  = 50;
    private static final int MEASURE_ROUNDS = 500;

    private static void runNaiveRegex() {
        java.util.regex.Pattern emailNaive =
            java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+\\.[a-z]{2,}");
        java.util.regex.Pattern ssnNaive =
            java.util.regex.Pattern.compile("\\d{3}-\\d{2}-\\d{4}");
        for (String text : CORPUS) {
            emailNaive.matcher(text).results().toList();
            ssnNaive.matcher(text).results().toList();
        }
    }

    private static void runSPGHeuristicOnly() {
        SPGConfig cfg = SPGConfig.builder().mlEnabled(false).build();
        SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(cfg);
        for (String text : CORPUS) spg.redact(text);
    }

    private static void runSPGFull() {
        SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();
        for (String text : CORPUS) spg.redact(text);
    }

    // ── False positive counting (on sentences that should be clean) ───────────

    private static final String[] CLEAN_SENTENCES = {
        "I ate an apple and watched the Apple keynote on YouTube.",
        "The quick brown fox jumps over the lazy dog.",
        "Pi is approximately 3.14159 and version 2.0.1 is current.",
        "In the novel, John meets Mary in chapter 5.",
        "Error 404 returned after 3 retries on node A7.",
    };

    private static int countFalsePositivesNaive() {
        java.util.regex.Pattern naive = java.util.regex.Pattern
            .compile("[\\w.+-]+@[\\w-]+\\.[a-z]{2,}|\\d{3}-\\d{2}-\\d{4}");
        int fp = 0;
        for (String s : CLEAN_SENTENCES) {
            Matcher m = naive.matcher(s);
            while (m.find()) fp++;
        }
        return fp;
    }

    private static int countFalsePositivesHeuristic() {
        SPGConfig cfg = SPGConfig.builder().mlEnabled(false).build();
        SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(cfg);
        int fp = 0;
        for (String s : CLEAN_SENTENCES) fp += spg.analyse(s).size();
        return fp;
    }

    private static int countFalsePositivesFull() {
        SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();
        int fp = 0;
        for (String s : CLEAN_SENTENCES) fp += spg.analyse(s).size();
        return fp;
    }

    // ── Timing utility ────────────────────────────────────────────────────────

    private static long timeMs(Runnable task, int rounds) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < rounds; i++) task.run();
        return System.currentTimeMillis() - start;
    }
}
