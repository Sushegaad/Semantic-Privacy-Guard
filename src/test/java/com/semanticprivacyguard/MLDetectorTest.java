package com.semanticprivacyguard;

import com.semanticprivacyguard.detector.MLDetector;
import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the ML (Naive Bayes context classifier) detection layer.
 *
 * <p>These tests verify the key disambiguation capability: the same surface
 * token should be flagged in PII contexts and ignored in benign contexts.</p>
 */
@DisplayName("MLDetector")
class MLDetectorTest {

    private MLDetector detector;

    @BeforeEach
    void setUp() {
        detector = new MLDetector();
    }

    // ── Name disambiguation ───────────────────────────────────────────────────

    @Test
    @DisplayName("flags a name preceded by 'dear'")
    void flagsNameAfterDear() {
        String text = "Dear Alice, please confirm your appointment.";
        List<PIIMatch> matches = detector.detect(text);
        assertTrue(
            matches.stream().anyMatch(m -> m.getType() == PIIType.PERSON_NAME
                && m.getValue().equals("Alice")),
            "Expected Alice to be flagged as a person name in a 'Dear Alice' context"
        );
    }

    @Test
    @DisplayName("flags a name preceded by 'contact'")
    void flagsNameAfterContact() {
        String text = "Please contact Bob for further information.";
        List<PIIMatch> matches = detector.detect(text);
        assertTrue(
            matches.stream().anyMatch(m -> m.getType() == PIIType.PERSON_NAME
                && m.getValue().equals("Bob")),
            "Expected Bob to be flagged as a person name in a 'contact Bob' context"
        );
    }

    @Test
    @DisplayName("does NOT flag 'Apple' when used as a fruit")
    void doesNotFlagAppleAsFruit() {
        String text = "I ate an apple yesterday.";
        List<PIIMatch> matches = detector.detect(text);
        // "apple" is lowercase here, so the ML layer won't even consider it
        assertFalse(
            matches.stream().anyMatch(m -> m.getValue().equalsIgnoreCase("apple")),
            "Apple as a common noun / fruit should not be flagged"
        );
    }

    @Test
    @DisplayName("flags 'Apple' as an organization when in contact context")
    void flagsAppleAsOrg() {
        String text = "Contact Apple at 555-0199 for support.";
        List<PIIMatch> matches = detector.detect(text);
        assertTrue(
            matches.stream().anyMatch(m ->
                m.getValue().equals("Apple")
                && (m.getType() == PIIType.ORGANIZATION
                    || m.getType() == PIIType.PERSON_NAME
                    || m.getType() == PIIType.GENERIC_PII)),
            "Apple in a 'contact Apple' context should be flagged as PII"
        );
    }

    // ── Classifier ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("classifier correctly labels PII context features")
    void classifierPIILabel() {
        // Direct classifier test
        var clf = com.semanticprivacyguard.ml.TrainingData.buildDefaultClassifier();
        String label = clf.predict(List.of("IS_TITLE_CASE", "PREV:dear", "LEN_MEDIUM"));
        assertEquals("PII", label, "Context with 'dear' + title case should be PII");
    }

    @Test
    @DisplayName("classifier correctly labels benign context features")
    void classifierBenignLabel() {
        var clf = com.semanticprivacyguard.ml.TrainingData.buildDefaultClassifier();
        String label = clf.predict(List.of("IS_TITLE_CASE", "ate", "fruit", "LEN_MEDIUM"));
        assertEquals("BENIGN", label, "Context with 'ate'+'fruit' should be BENIGN");
    }

    @Test
    @DisplayName("predictProba returns probabilities summing to ~1.0")
    void probasSum() {
        var clf = com.semanticprivacyguard.ml.TrainingData.buildDefaultClassifier();
        double[] proba = clf.predictProba(List.of("IS_TITLE_CASE", "PREV:dear"));
        double sum = 0.0;
        for (double p : proba) sum += p;
        assertEquals(1.0, sum, 1e-9, "Probabilities should sum to 1.0");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

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
    @DisplayName("does not flag common lowercase stop words")
    void doesNotFlagStopWords() {
        String text = "the quick brown fox";
        // All lowercase: ML layer won't consider them candidates
        assertEquals(0, detector.detect(text).size());
    }
}
