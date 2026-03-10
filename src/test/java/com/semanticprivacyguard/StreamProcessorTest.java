package com.semanticprivacyguard;

import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.stream.StreamProcessor;
import com.semanticprivacyguard.stream.StreamRedactionSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link StreamProcessor} and the convenience stream methods
 * on {@link SemanticPrivacyGuard}.
 */
@DisplayName("StreamProcessor tests")
class StreamProcessorTest {

    private final SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static InputStream streamOf(String... lines) {
        String content = String.join(System.lineSeparator(), lines);
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String capture(OutputStream out) {
        return ((ByteArrayOutputStream) out).toString(StandardCharsets.UTF_8);
    }

    // ── Basic redaction ───────────────────────────────────────────────────────

    @Test
    @DisplayName("redacts PII in a single-line stream")
    void singleLineRedaction() throws IOException {
        InputStream  in  = streamOf("Contact alice@example.com for details.");
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        String result = capture(out);
        assertFalse(result.contains("alice@example.com"), "Email should be redacted");
        assertTrue(result.contains("[EMAIL_1]"),          "Token should appear");
        assertEquals(1, summary.getTotalLines());
        assertEquals(1, summary.getLinesWithPII());
        assertTrue(summary.getTotalMatches() >= 1);
    }

    @Test
    @DisplayName("redacts PII across multiple lines")
    void multiLineRedaction() throws IOException {
        InputStream in = streamOf(
            "Send report to alice@example.com please.",
            "This line is clean.",
            "SSN: 123-45-6789 is sensitive."
        );
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        String result = capture(out);
        assertFalse(result.contains("alice@example.com"));
        assertFalse(result.contains("123-45-6789"));
        assertTrue(result.contains("[EMAIL_1]"));
        assertTrue(result.contains("[SSN_1]"));

        assertEquals(3, summary.getTotalLines());
        assertEquals(2, summary.getLinesWithPII(),  "2 of 3 lines have PII");
        assertTrue(summary.getTotalMatches() >= 2);
    }

    // ── Document-scoped token counters ────────────────────────────────────────

    @Test
    @DisplayName("token counters are document-scoped — no two EMAIL_1 tokens")
    void documentScopedCounters() throws IOException {
        InputStream in = streamOf(
            "First email: alice@example.com",
            "Clean line here.",
            "Second email: bob@example.com"
        );
        OutputStream out = new ByteArrayOutputStream();

        spg.redactStream(in, out);

        String result = capture(out);
        assertTrue(result.contains("[EMAIL_1]"), "First email → EMAIL_1");
        assertTrue(result.contains("[EMAIL_2]"), "Second email → EMAIL_2");

        // EMAIL_1 must appear exactly once — not reused on the second PII line
        // split("regex", -1) returns n+1 parts for n occurrences of the pattern
        int countEmail1 = result.split("\\[EMAIL_1\\]", -1).length - 1;
        assertEquals(1, countEmail1, "EMAIL_1 should appear exactly once");
    }

    // ── Summary stats ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("summary reports correct matchCountByType")
    void summaryMatchCountByType() throws IOException {
        InputStream in = streamOf(
            "Email: alice@example.com",
            "Email: bob@example.com",
            "SSN: 123-45-6789"
        );
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        assertTrue(summary.getMatchCountByType().getOrDefault(PIIType.EMAIL, 0L) >= 2,
            "Should count at least 2 EMAIL matches");
        assertTrue(summary.getMatchCountByType().getOrDefault(PIIType.SSN, 0L) >= 1,
            "Should count at least 1 SSN match");
    }

    @Test
    @DisplayName("summary hasPII() is false for clean input")
    void summaryCleanInput() throws IOException {
        InputStream  in  = streamOf("The quick brown fox.", "No PII here.");
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        assertFalse(summary.hasPII());
        assertEquals(0, summary.getTotalMatches());
        assertEquals(2, summary.getTotalLines());
        assertEquals(0, summary.getLinesWithPII());
        assertEquals(0.0, summary.getPIILineRatio());
    }

    @Test
    @DisplayName("getPIILineRatio() returns correct fraction")
    void piiLineRatio() throws IOException {
        InputStream in = streamOf(
            "alice@example.com",   // PII
            "clean",               // clean
            "bob@example.com",     // PII
            "also clean"           // clean
        );
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        assertEquals(4, summary.getTotalLines());
        assertEquals(2, summary.getLinesWithPII());
        assertEquals(0.5, summary.getPIILineRatio(), 0.001);
    }

    // ── Empty / edge cases ────────────────────────────────────────────────────

    @Test
    @DisplayName("empty input produces zero-line summary")
    void emptyInput() throws IOException {
        InputStream  in  = new ByteArrayInputStream(new byte[0]);
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);

        assertEquals(0, summary.getTotalLines());
        assertEquals(0, summary.getTotalMatches());
        assertFalse(summary.hasPII());
        assertEquals("", capture(out));
    }

    @Test
    @DisplayName("blank lines pass through unchanged")
    void blankLinesPassThrough() throws IOException {
        InputStream in = streamOf("", "alice@example.com", "");
        OutputStream out = new ByteArrayOutputStream();

        spg.redactStream(in, out);

        String result = capture(out);
        assertFalse(result.contains("alice@example.com"));
        assertTrue(result.contains("[EMAIL_1]"));
    }

    // ── Reader / Writer API ───────────────────────────────────────────────────

    @Test
    @DisplayName("Reader/Writer API redacts correctly")
    void readerWriterApi() throws IOException {
        String input = "SSN: 123-45-6789\nclean line\n";
        Reader  reader = new StringReader(input);
        Writer  writer = new StringWriter();

        StreamRedactionSummary summary = spg.redactStream(reader, writer);

        String result = writer.toString();
        assertFalse(result.contains("123-45-6789"));
        assertTrue(result.contains("[SSN_1]"));
        assertEquals(2, summary.getTotalLines());
        assertEquals(1, summary.getLinesWithPII());
    }

    // ── Path API ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Path API redacts file to file")
    void pathApi(@TempDir Path tempDir) throws IOException {
        Path input  = tempDir.resolve("input.txt");
        Path output = tempDir.resolve("output.txt");

        Files.writeString(input,
            "Contact john.doe@acme.com\nSSN: 123-45-6789\nAll good here.\n");

        StreamRedactionSummary summary = spg.redactPath(input, output);

        assertTrue(Files.exists(output), "Output file should be created");
        String result = Files.readString(output);

        assertFalse(result.contains("john.doe@acme.com"));
        assertFalse(result.contains("123-45-6789"));
        assertTrue(result.contains("[EMAIL_1]"));
        assertTrue(result.contains("[SSN_1]"));

        assertEquals(3, summary.getTotalLines());
        assertEquals(2, summary.getLinesWithPII());
        assertTrue(summary.getTotalMatches() >= 2);
    }

    // ── redactLines (Stream<String>) ──────────────────────────────────────────

    @Test
    @DisplayName("redactLines() redacts a sequential Stream<String>")
    void redactLinesStream() {
        StreamProcessor proc = spg.streamProcessor();

        List<String> redacted = proc.redactLines(
            Stream.of("alice@example.com", "clean", "bob@example.com")
        ).toList();

        assertEquals(3, redacted.size());
        assertFalse(redacted.get(0).contains("alice@example.com"));
        assertTrue(redacted.get(0).contains("[EMAIL_1]"));
        assertEquals("clean", redacted.get(1));
        assertFalse(redacted.get(2).contains("bob@example.com"));
        assertTrue(redacted.get(2).contains("[EMAIL_2]"),
            "Second email should be EMAIL_2, not EMAIL_1");
    }

    @Test
    @DisplayName("redactLines() rejects parallel streams")
    void redactLinesRejectsParallel() {
        StreamProcessor proc = spg.streamProcessor();
        Stream<String> parallel = Stream.of("a", "b").parallel();

        assertThrows(IllegalArgumentException.class,
            () -> proc.redactLines(parallel).toList(),
            "Parallel streams should be rejected");
    }

    // ── Summary toString ──────────────────────────────────────────────────────

    @Test
    @DisplayName("StreamRedactionSummary.toString() contains key fields")
    void summaryToString() throws IOException {
        InputStream  in  = streamOf("alice@example.com");
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);
        String str = summary.toString();

        assertTrue(str.contains("lines="),      "Should contain lines= field");
        assertTrue(str.contains("matches="),    "Should contain matches= field");
        assertTrue(str.contains("linesWithPII="), "Should contain linesWithPII= field");
        assertTrue(str.contains("timeMs="),     "Should contain timeMs= field");
    }

    @Test
    @DisplayName("processingTimeMs is non-negative")
    void processingTimeNonNegative() throws IOException {
        InputStream  in  = streamOf("test line");
        OutputStream out = new ByteArrayOutputStream();

        StreamRedactionSummary summary = spg.redactStream(in, out);
        assertTrue(summary.getProcessingTimeMs() >= 0);
    }
}
