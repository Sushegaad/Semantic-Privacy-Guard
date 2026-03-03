package com.semanticprivacyguard;

import com.semanticprivacyguard.detector.HeuristicDetector;
import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the heuristic (regex-based) PII detection layer.
 *
 * <p>Tests follow the pattern: positive cases (must detect), negative cases
 * (must NOT detect), and edge cases (boundary conditions).</p>
 */
@DisplayName("HeuristicDetector")
class HeuristicDetectorTest {

    private HeuristicDetector detector;

    @BeforeEach
    void setUp() {
        detector = new HeuristicDetector();
    }

    // ── SSN ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("SSN detection")
    class SSNTests {

        @ParameterizedTest(name = "should detect valid SSN: {0}")
        @ValueSource(strings = {
            "123-45-6789",
            "001-01-0001",
            "899-99-9999",
        })
        void detectsValidSSN(String ssn) {
            List<PIIMatch> matches = detector.detect("SSN: " + ssn);
            assertTrue(hasType(matches, PIIType.SSN),
                "Expected SSN match for: " + ssn);
        }

        @ParameterizedTest(name = "should NOT detect invalid SSN: {0}")
        @ValueSource(strings = {
            "000-12-3456",   // area 000 — never assigned
            "666-12-3456",   // area 666 — reserved
            "900-12-3456",   // area 9xx — reserved (ITIN range)
            "999-12-3456",   // area 9xx — reserved
            "123-00-4567",   // group 00 — never valid
            "123-45-0000",   // serial 0000 — never valid
            "123456789",     // undashed — too many false positives
        })
        void rejectsInvalidSSN(String ssn) {
            List<PIIMatch> matches = detector.detect("Number: " + ssn);
            assertFalse(hasType(matches, PIIType.SSN),
                "Did not expect SSN match for: " + ssn);
        }

        @Test
        @DisplayName("detects multiple SSNs in one string")
        void detectsMultipleSSNs() {
            String text = "Alice: 123-45-6789, Bob: 234-56-7890";
            List<PIIMatch> matches = detector.detect(text);
            long count = matches.stream().filter(m -> m.getType() == PIIType.SSN).count();
            assertEquals(2, count, "Expected exactly 2 SSN matches");
        }
    }

    // ── Credit Card ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Credit card detection")
    class CreditCardTests {

        @Test
        @DisplayName("detects valid Visa card (Luhn passes)")
        void detectsVisaCard() {
            // Luhn-valid 16-digit Visa
            String text = "Card: 4532015112830366";
            List<PIIMatch> matches = detector.detect(text);
            assertTrue(hasType(matches, PIIType.CREDIT_CARD));
        }

        @Test
        @DisplayName("rejects card that fails Luhn check")
        void rejectsLuhnFailCard() {
            String text = "Card: 4532015112830000"; // invalid Luhn
            List<PIIMatch> matches = detector.detect(text);
            assertFalse(hasType(matches, PIIType.CREDIT_CARD));
        }

        @Test
        @DisplayName("detects space-separated card number")
        void detectsSpaceSeparatedCard() {
            String text = "Pay with 4532 0151 1283 0366";
            List<PIIMatch> matches = detector.detect(text);
            assertTrue(hasType(matches, PIIType.CREDIT_CARD));
        }
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Email detection")
    class EmailTests {

        @ParameterizedTest(name = "detects email: {0}")
        @ValueSource(strings = {
            "alice@example.com",
            "user.name+tag@sub.domain.org",
            "admin@company.co.uk",
        })
        void detectsEmails(String email) {
            assertTrue(hasType(detector.detect(email), PIIType.EMAIL));
        }

        @ParameterizedTest(name = "does not flag as email: {0}")
        @ValueSource(strings = {
            "not-an-email",
            "missing@",
            "@nodomain.com",
        })
        void rejectsNonEmails(String input) {
            assertFalse(hasType(detector.detect(input), PIIType.EMAIL));
        }
    }

    // ── Phone ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Phone number detection")
    class PhoneTests {

        @ParameterizedTest(name = "detects phone: {0}")
        @ValueSource(strings = {
            "(555) 867-5309",
            "555-867-5309",
            "555.867.5309",
            "+1 555 867 5309",
        })
        void detectsPhones(String phone) {
            assertTrue(hasType(detector.detect(phone), PIIType.PHONE),
                "Expected PHONE match for: " + phone);
        }
    }

    // ── IP Address ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IP address detection")
    class IPTests {

        @Test
        @DisplayName("detects valid IPv4")
        void detectsIPv4() {
            assertTrue(hasType(detector.detect("Server at 192.168.1.100"), PIIType.IP_ADDRESS));
        }

        @Test
        @DisplayName("does not detect out-of-range IPv4 octet")
        void rejectsInvalidIPv4() {
            assertFalse(hasType(detector.detect("version 256.1.2.3"), PIIType.IP_ADDRESS));
        }

        @Test
        @DisplayName("detects valid IPv6")
        void detectsIPv6() {
            assertTrue(hasType(
                detector.detect("IPv6: 2001:0db8:85a3:0000:0000:8a2e:0370:7334"),
                PIIType.IP_ADDRESS));
        }
    }

    // ── API Key ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("API key detection")
    class ApiKeyTests {

        @Test
        @DisplayName("detects AWS access key ID")
        void detectsAwsKey() {
            String text = "export AWS_KEY=AKIAIOSFODNN7EXAMPLE";
            assertTrue(hasType(detector.detect(text), PIIType.API_KEY));
        }

        @Test
        @DisplayName("detects OpenAI-style secret key")
        void detectsOpenAIKey() {
            String text = "key: sk-abcdefghijklmnopqrstuvwxyz123456";
            assertTrue(hasType(detector.detect(text), PIIType.API_KEY));
        }

        @Test
        @DisplayName("detects GitHub personal access token")
        void detectsGitHubToken() {
            String text = "token ghp_abcdefghijklmnopqrstuvwxyz1234567890";
            assertTrue(hasType(detector.detect(text), PIIType.API_KEY));
        }
    }

    // ── Password ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Password detection")
    class PasswordTests {

        @Test
        @DisplayName("detects password= pattern")
        void detectsPasswordEquals() {
            assertTrue(hasType(
                detector.detect("password=MySuperSecret!"),
                PIIType.PASSWORD));
        }

        @Test
        @DisplayName("detects pwd: pattern")
        void detectsPwdColon() {
            assertTrue(hasType(
                detector.detect("pwd: hunter2"),
                PIIType.PASSWORD));
        }
    }

    // ── IBAN ──────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("IBAN detection")
    class IBANTests {

        @Test
        @DisplayName("detects a valid IBAN")
        void detectsIBAN() {
            String text = "Transfer to GB29NWBK60161331926819 please";
            assertTrue(hasType(detector.detect(text), PIIType.BANK_ACCOUNT));
        }
    }

    // ── Empty / null input ────────────────────────────────────────────────────

    @Test
    @DisplayName("returns empty list for null input")
    void handlesNull() {
        assertEquals(0, detector.detect(null).size());
    }

    @Test
    @DisplayName("returns empty list for blank input")
    void handlesBlank() {
        assertEquals(0, detector.detect("   ").size());
    }

    @Test
    @DisplayName("returns empty list for clean text")
    void handlesCleanText() {
        String text = "The quick brown fox jumps over the lazy dog.";
        assertEquals(0, detector.detect(text).size());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private boolean hasType(List<PIIMatch> matches, PIIType type) {
        return matches.stream().anyMatch(m -> m.getType() == type);
    }
}
