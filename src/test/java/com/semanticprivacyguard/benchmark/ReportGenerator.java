package com.semanticprivacyguard.benchmark;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

/**
 * Serialises benchmark results to {@code docs/benchmark-results.json}.
 *
 * <h2>No external dependencies</h2>
 * <p>JSON is written manually with a {@link StringBuilder} so that no
 * Jackson / Gson dependency is required in test scope.  The output is
 * intentionally pretty-printed for human readability and git-diff friendliness.</p>
 *
 * <h2>Output location</h2>
 * <p>The JSON file is written to {@code <user.dir>/docs/benchmark-results.json}.
 * {@code user.dir} is set by Maven to the project root, so the path resolves
 * correctly whether the test is run from an IDE or from the CLI.</p>
 */
public final class ReportGenerator {

    private ReportGenerator() { /* utility class */ }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Writes a machine-readable JSON report of the supplied benchmark results.
     *
     * <p>The {@code docs/} directory is created if it does not already exist.
     * Any pre-existing {@code benchmark-results.json} is overwritten atomically
     * (write to same path — filesystem guarantees last-write-wins on most OS).</p>
     *
     * @param results ordered list of {@link BenchmarkResult} instances to serialise
     * @throws RuntimeException wrapping any {@link IOException} if the file cannot
     *                          be written (permissions, disk full, etc.)
     */
    public static void writeJson(List<BenchmarkResult> results) {
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path docsDir     = projectRoot.resolve("docs");
        Path jsonFile    = docsDir.resolve("benchmark-results.json");

        try {
            Files.createDirectories(docsDir);
            String json = buildJson(results);
            try (Writer w = Files.newBufferedWriter(jsonFile, StandardCharsets.UTF_8)) {
                w.write(json);
            }
            System.out.println("[ReportGenerator] Written: " + jsonFile.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException(
                "Failed to write benchmark JSON to " + jsonFile + ": " + e.getMessage(), e);
        }
    }

    // ── JSON builder ──────────────────────────────────────────────────────────

    /**
     * Builds the full JSON document as a {@link String}.
     *
     * <p>Schema:</p>
     * <pre>
     * {
     *   "generatedAt": "2026-05-25T12:34:56.789Z",
     *   "configurations": [
     *     {
     *       "name": "SPG Heuristic-only",
     *       "macroPrecision": 0.91,
     *       "macroRecall": 0.83,
     *       "macroF1": 0.87,
     *       "throughputMbps": 390.0,
     *       "peakHeapMb": 18,
     *       "timingMs": 42
     *     },
     *     ...
     *   ]
     * }
     * </pre>
     */
    private static String buildJson(List<BenchmarkResult> results) {
        StringBuilder sb = new StringBuilder(512);

        sb.append("{\n");
        sb.append("  \"generatedAt\": \"").append(escapeJson(Instant.now().toString())).append("\",\n");
        sb.append("  \"configurations\": [\n");

        for (int i = 0; i < results.size(); i++) {
            BenchmarkResult r = results.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(escapeJson(r.configName())).append("\",\n");
            sb.append("      \"macroPrecision\": ").append(formatDouble(r.macroPrecision())).append(",\n");
            sb.append("      \"macroRecall\": ").append(formatDouble(r.macroRecall())).append(",\n");
            sb.append("      \"macroF1\": ").append(formatDouble(r.macroF1())).append(",\n");
            sb.append("      \"throughputMbps\": ").append(formatDouble(r.throughputMbps())).append(",\n");
            sb.append("      \"peakHeapMb\": ").append(r.peakHeapMb()).append(",\n");
            sb.append("      \"timingMs\": ").append(r.timingMs()).append("\n");
            sb.append("    }");
            if (i < results.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ]\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Formats a {@code double} to at most 4 decimal places, stripping trailing
     * zeros so that {@code 1.0} renders as {@code 1.0} rather than {@code 1.0000}.
     */
    private static String formatDouble(double value) {
        // Round to 4 dp then strip unnecessary trailing zeros
        String s = String.format("%.4f", value);
        // Remove trailing zeros after the decimal point, but keep at least one digit
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "");
            if (s.endsWith(".")) s = s + "0";
        }
        return s;
    }

    /**
     * Minimal JSON string escaping — handles the characters that are most likely
     * to appear in configuration names and timestamps.
     */
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
