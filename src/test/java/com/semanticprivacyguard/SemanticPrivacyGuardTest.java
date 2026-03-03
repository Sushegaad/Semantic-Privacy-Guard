package com.semanticprivacyguard;

import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.model.RedactionResult;
import com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the top-level {@link SemanticPrivacyGuard} facade.
 */
@DisplayName("SemanticPrivacyGuard integration tests")
class SemanticPrivacyGuardTest {

    private final SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

    // ── Basic redaction ───────────────────────────────────────────────────────

    @Test
    @DisplayName("redacts email address in plain sentence")
    void redactsEmail() {
        RedactionResult r = spg.redact("Send report to alice@example.com please.");
        assertFalse(r.getRedactedText().contains("alice@example.com"));
        assertTrue(r.getRedactedText().contains("[EMAIL_1]"));
        assertEquals(1, r.getMatchCount());
    }

    @Test
    @DisplayName("redacts SSN in plain sentence")
    void redactsSSN() {
        RedactionResult r = spg.redact("SSN: 123-45-6789");
        assertFalse(r.getRedactedText().contains("123-45-6789"));
        assertTrue(r.getRedactedText().contains("[SSN_1]"));
    }

    @Test
    @DisplayName("redacts phone number")
    void redactsPhone() {
        RedactionResult r = spg.redact("Call me at (555) 867-5309 after 5pm.");
        assertFalse(r.getRedactedText().contains("867-5309"));
        assertTrue(r.containsPII());
    }

    @Test
    @DisplayName("redacts valid credit card number")
    void redactsCreditCard() {
        // Luhn-valid Visa test number
        RedactionResult r = spg.redact("Card: 4532015112830366");
        assertFalse(r.getRedactedText().contains("4532015112830366"));
        assertTrue(r.containsPII());
    }

    @Test
    @DisplayName("handles multiple PII types in one string")
    void redactsMultiplePIITypes() {
        // Phone exchange must start with [2-9] per NANP (exchanges 0xx/1xx are reserved).
        // "(555) 234-5678" uses area=555, exchange=234, subscriber=5678 — all valid NANP.
        String text = "Contact john.doe@acme.com or call (555) 234-5678. SSN: 123-45-6789";
        RedactionResult r = spg.redact(text);
        assertTrue(r.getMatchCount() >= 3, "Expected at least 3 PII matches");
        assertFalse(r.getRedactedText().contains("john.doe@acme.com"));
        assertFalse(r.getRedactedText().contains("123-45-6789"));
    }

    // ── Reverse map ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("reverse map allows de-tokenisation")
    void reverseMapWorks() {
        RedactionResult r = spg.redact("Email alice@example.com now.");
        assertTrue(r.getReverseMap().containsValue("alice@example.com"),
            "Reverse map should contain the original email");
    }

    @Test
    @DisplayName("reverse map is empty when disabled")
    void reverseMapDisabled() {
        SPGConfig cfg = SPGConfig.builder().buildReverseMap(false).build();
        SemanticPrivacyGuard guard = SemanticPrivacyGuard.create(cfg);
        RedactionResult r = guard.redact("Send to alice@example.com");
        assertTrue(r.getReverseMap().isEmpty());
    }

    // ── MASK mode ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("MASK mode replaces PII with block characters")
    void maskMode() {
        SPGConfig cfg = SPGConfig.builder().redactionMode(RedactionMode.MASK).build();
        SemanticPrivacyGuard guard = SemanticPrivacyGuard.create(cfg);
        String out = guard.redact("Email: alice@example.com").getRedactedText();
        assertFalse(out.contains("alice@example.com"));
        assertTrue(out.contains("█"), "Mask mode should use block characters");
    }

    // ── BLANK mode ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("BLANK mode replaces PII with [REDACTED]")
    void blankMode() {
        SPGConfig cfg = SPGConfig.builder().redactionMode(RedactionMode.BLANK).build();
        SemanticPrivacyGuard guard = SemanticPrivacyGuard.create(cfg);
        String out = guard.redact("SSN: 123-45-6789").getRedactedText();
        assertTrue(out.contains("[REDACTED]"), "Blank mode should use [REDACTED] token");
    }

    // ── Minimum severity filter ───────────────────────────────────────────────

    @Test
    @DisplayName("minimumSeverity=8 only catches highest-impact PII")
    void minimumSeverityFilter() {
        SPGConfig cfg = SPGConfig.builder().minimumSeverity(8).build();
        SemanticPrivacyGuard guard = SemanticPrivacyGuard.create(cfg);

        // SSN (severity 10) should be caught
        assertTrue(guard.containsPII("SSN: 123-45-6789"),
            "SSN (severity 10) should be caught at threshold 8");
    }

    // ── Type filter ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("enabledTypes limits detection to specified types")
    void enabledTypesFilter() {
        SPGConfig cfg = SPGConfig.builder()
            .enabledTypes(Set.of(PIIType.EMAIL))
            .build();
        SemanticPrivacyGuard guard = SemanticPrivacyGuard.create(cfg);

        String text = "SSN: 123-45-6789, email: alice@example.com";
        RedactionResult r = guard.redact(text);

        // Email should be redacted
        assertFalse(r.getRedactedText().contains("alice@example.com"));
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clean text returns isClean()=true")
    void cleanText() {
        RedactionResult r = spg.redact("The quick brown fox jumps over the lazy dog.");
        assertTrue(r.isClean());
        assertEquals(0, r.getMatchCount());
    }

    @Test
    @DisplayName("null input returns empty result without throwing")
    void nullInput() {
        assertDoesNotThrow(() -> {
            RedactionResult r = spg.redact(null);
            assertNotNull(r);
            assertTrue(r.isClean());
        });
    }

    @Test
    @DisplayName("containsPII returns false for clean text")
    void containsPIIClean() {
        assertFalse(spg.containsPII("Hello world"));
    }

    @Test
    @DisplayName("processingTimeMs is non-negative")
    void processingTime() {
        RedactionResult r = spg.redact("alice@example.com");
        assertTrue(r.getProcessingTimeMs() >= 0);
    }

    @Test
    @DisplayName("same type is counter-incremented")
    void sameTypeCounterIncrements() {
        RedactionResult r = spg.redact(
            "alice@example.com and bob@example.com");
        String text = r.getRedactedText();
        assertTrue(text.contains("[EMAIL_1]") && text.contains("[EMAIL_2]"),
            "Multiple emails should get separate numeric suffixes");
    }

    @Test
    @DisplayName("analyse returns matches without modifying text")
    void analyseReturnsMatches() {
        var matches = spg.analyse("SSN: 123-45-6789");
        assertFalse(matches.isEmpty());
        assertEquals(PIIType.SSN, matches.get(0).getType());
    }
}
