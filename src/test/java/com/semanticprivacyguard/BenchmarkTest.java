package com.semanticprivacyguard;

import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.semanticprivacyguard.config.SPGConfig;

/**
 * Benchmark suite — run with: {@code mvn test -P benchmark}
 *
 * <p>Measures and compares throughput and accuracy of three approaches:</p>
 * <ol>
 *   <li><b>Naive regex</b> — a single, unsophisticated email+SSN regex with no
 *       post-processing or false-positive filtering.</li>
 *   <li><b>SPG Heuristic-only</b> — the HeuristicDetector with Luhn validation
 *       and entropy filtering but no ML.</li>
 *   <li><b>SPG Full (Heuristic + ML)</b> — the complete pipeline.</li>
 * </ol>
 *
 * <p>Results are printed to stdout. This class intentionally does not use a
 * framework like JMH to keep the dependency count at zero; the numbers are
 * sufficient for order-of-magnitude comparisons on commodity hardware.</p>
 */
@DisplayName("Benchmark: SPG vs naive regex vs heuristic-only")
class BenchmarkTest {

    // ── Benchmark corpus ──────────────────────────────────────────────────────

    private static final String[] CORPUS = {
        // True positives
        "Dear Alice Johnson, your SSN is 234-56-7890 and email is alice.j@corp.com",
        "Call support at (800) 555-1234 or email help@example.org",
        "Password: S3cr3tP@ss! and API key: sk-abcdefghijklmnopqrstuvwxyz1234",
        "Patient ID: 123-45-6789, DOB: 03/15/1985, referred by Dr. Smith",
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

    // ── Approach 1: naive regex (no Luhn, no entropy, no context) ─────────────

    @Test
    @DisplayName("Benchmark: naive regex vs SPG")
    void benchmark() {
        // Warm up JIT
        for (int i = 0; i < WARMUP_ROUNDS; i++) {
            runNaiveRegex();
            runSPGHeuristicOnly();
            runSPGFull();
        }

        // Measure
        long naiveMs   = timeMs(BenchmarkTest::runNaiveRegex,   MEASURE_ROUNDS);
        long heurMs    = timeMs(BenchmarkTest::runSPGHeuristicOnly, MEASURE_ROUNDS);
        long fullMs    = timeMs(BenchmarkTest::runSPGFull,       MEASURE_ROUNDS);

        // Accuracy (false positive count on clean sentences)
        int naiveFP  = countFalsePositivesNaive();
        int heurFP   = countFalsePositivesHeuristic();
        int fullFP   = countFalsePositivesFull();

        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║              SPG Benchmark Results (" + MEASURE_ROUNDS + " rounds)              ║");
        System.out.println("╠═══════════════════════════╦════════════╦════════════════════╣");
        System.out.println("║ Approach                  ║ Time (ms)  ║ False Positives    ║");
        System.out.println("╠═══════════════════════════╬════════════╬════════════════════╣");
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "Naive Regex",       naiveMs, naiveFP);
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "SPG Heuristic-only", heurMs, heurFP);
        System.out.printf ("║ %-25s ║ %10d ║ %18d ║%n", "SPG Full (H+ML)",   fullMs,  fullFP);
        System.out.println("╚═══════════════════════════╩════════════╩════════════════════╝");
        System.out.printf ("%nML disambiguation reduces false positives by %d (%.0f%% improvement)%n",
            (naiveFP - fullFP),
            naiveFP > 0 ? 100.0 * (naiveFP - fullFP) / naiveFP : 0.0);

        // Assert SPG is at least as accurate as naive regex
        assertTrue(fullFP <= naiveFP,
            "SPG should not produce more false positives than naive regex");
    }

    // ── Benchmark implementations ─────────────────────────────────────────────

    private static void runNaiveRegex() {
        // A single naive pattern with no validation
        java.util.regex.Pattern emailNaive = java.util.regex.Pattern
            .compile("[\\w.+-]+@[\\w-]+\\.[a-z]{2,}");
        java.util.regex.Pattern ssnNaive   = java.util.regex.Pattern
            .compile("\\d{3}-\\d{2}-\\d{4}");
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
