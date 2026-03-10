package com.semanticprivacyguard;

import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.nlp.NLPModelException;
import com.semanticprivacyguard.nlp.NLPModelLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the OpenNLP integration layer.
 *
 * <p>These tests are designed to run in CI <em>without</em> any OpenNLP model
 * files present.  They verify the error-handling paths (model-not-found) and
 * the configuration API.  Detection-accuracy tests that require real model
 * files are in the integration test suite and are skipped in CI unless the
 * {@code NLP_MODELS_DIR} environment variable points to a directory containing
 * the required {@code .bin} files.</p>
 */
@DisplayName("NLP integration tests")
class NLPDetectorTest {

    // ── Model loading error paths ─────────────────────────────────────────────

    @Test
    @DisplayName("NLPModelLoader.fromClasspath() throws when no models on classpath")
    void fromClasspathThrowsWhenModelsMissing() {
        // In CI / unit test runs there are no OpenNLP models on the classpath.
        // This verifies the error path produces the right exception type.
        assertThrows(NLPModelException.class,
            NLPModelLoader::fromClasspath,
            "Should throw NLPModelException when models are absent from classpath");
    }

    @Test
    @DisplayName("NLPModelLoader.fromDirectory() throws for non-existent directory")
    void fromDirectoryThrowsForMissingDir(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");
        assertThrows(NLPModelException.class,
            () -> NLPModelLoader.fromDirectory(nonExistent),
            "Should throw NLPModelException for missing directory");
    }

    @Test
    @DisplayName("NLPModelLoader.fromDirectory() throws when required person model absent")
    void fromDirectoryThrowsWhenPersonModelMissing(@TempDir Path tempDir) {
        // The directory exists but contains no model files
        assertThrows(NLPModelException.class,
            () -> NLPModelLoader.fromDirectory(tempDir),
            "Should throw NLPModelException when en-ner-person.bin is absent");
    }

    // ── SemanticPrivacyGuard integration ──────────────────────────────────────

    @Test
    @DisplayName("SemanticPrivacyGuard.create() throws IllegalStateException when NLP enabled without models")
    void guardThrowsIllegalStateWhenNlpEnabledAndModelsMissing() {
        SPGConfig cfg = SPGConfig.builder().nlpEnabled(true).build();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> SemanticPrivacyGuard.create(cfg),
            "Should throw when NLP is enabled but classpath models are absent");

        // Verify the error message contains actionable guidance
        String msg = ex.getMessage();
        assertTrue(msg.contains("NLP") || msg.contains("model"),
            "Exception message should mention NLP or model: " + msg);
    }

    @Test
    @DisplayName("SemanticPrivacyGuard.create() throws when NLP enabled with bad models directory")
    void guardThrowsWhenNlpEnabledWithBadDirectory(@TempDir Path tempDir) {
        Path emptyDir = tempDir.resolve("empty");
        emptyDir.toFile().mkdirs();

        SPGConfig cfg = SPGConfig.builder()
            .nlpEnabled(true)
            .nlpModelsDirectory(emptyDir)
            .build();

        assertThrows(IllegalStateException.class,
            () -> SemanticPrivacyGuard.create(cfg),
            "Should throw when model directory lacks en-ner-person.bin");
    }

    // ── SPGConfig NLP options ─────────────────────────────────────────────────

    @Test
    @DisplayName("nlpEnabled defaults to false")
    void nlpDisabledByDefault() {
        SPGConfig cfg = SPGConfig.defaults();
        assertFalse(cfg.isNlpEnabled(), "NLP should be off by default");
    }

    @Test
    @DisplayName("nlpEnabled(true) is reflected in config")
    void nlpEnabledFlagStoredCorrectly() {
        SPGConfig cfg = SPGConfig.builder().nlpEnabled(true).build();
        assertTrue(cfg.isNlpEnabled());
    }

    @Test
    @DisplayName("nlpModelsDirectory is null by default (classpath mode)")
    void nlpModelsDirectoryNullByDefault() {
        SPGConfig cfg = SPGConfig.defaults();
        assertNull(cfg.getNlpModelsDirectory(), "Default models directory should be null (classpath)");
    }

    @Test
    @DisplayName("nlpModelsDirectory is stored correctly")
    void nlpModelsDirectoryStored(@TempDir Path dir) {
        SPGConfig cfg = SPGConfig.builder()
            .nlpEnabled(true)
            .nlpModelsDirectory(dir)
            .build();
        assertEquals(dir, cfg.getNlpModelsDirectory());
    }

    @Test
    @DisplayName("nlpConfidenceThreshold defaults to 0.70")
    void nlpThresholdDefault() {
        SPGConfig cfg = SPGConfig.defaults();
        assertEquals(SPGConfig.DEFAULT_NLP_THRESHOLD, cfg.getNlpConfidenceThreshold(), 0.001);
    }

    @Test
    @DisplayName("nlpConfidenceThreshold set via builder")
    void nlpThresholdSetViaBuilder() {
        SPGConfig cfg = SPGConfig.builder()
            .nlpConfidenceThreshold(0.85)
            .build();
        assertEquals(0.85, cfg.getNlpConfidenceThreshold(), 0.001);
    }

    @Test
    @DisplayName("nlpConfidenceThreshold rejects out-of-range values")
    void nlpThresholdValidation() {
        assertThrows(IllegalArgumentException.class,
            () -> SPGConfig.builder().nlpConfidenceThreshold(0.0));
        assertThrows(IllegalArgumentException.class,
            () -> SPGConfig.builder().nlpConfidenceThreshold(1.1));
    }

    // ── SPGConfig validation ──────────────────────────────────────────────────

    @Test
    @DisplayName("build() succeeds when only NLP enabled")
    void buildSucceedsWithOnlyNlpEnabled() {
        // Validation should pass — NLP alone is a valid configuration.
        // (Guard construction will later fail on missing models, but the config itself is valid.)
        assertDoesNotThrow(() ->
            SPGConfig.builder()
                .heuristicEnabled(false)
                .mlEnabled(false)
                .nlpEnabled(true)
                .build());
    }

    @Test
    @DisplayName("build() throws when all detectors disabled")
    void buildThrowsWhenAllDetectorsDisabled() {
        assertThrows(IllegalStateException.class,
            () -> SPGConfig.builder()
                .heuristicEnabled(false)
                .mlEnabled(false)
                .nlpEnabled(false)
                .build());
    }

    @Test
    @DisplayName("toString includes NLP fields")
    void toStringContainsNlpFields() {
        SPGConfig cfg = SPGConfig.builder().nlpEnabled(true).build();
        String str = cfg.toString();
        assertTrue(str.contains("nlp=true"),  "toString should show nlp=true");
        assertTrue(str.contains("nlpThreshold"), "toString should show nlpThreshold");
    }

    // ── NLPModelException message quality ─────────────────────────────────────

    @Test
    @DisplayName("NLPModelException message includes actionable download hint")
    void nlpModelExceptionMessageIsHelpful() {
        NLPModelException ex = assertThrows(NLPModelException.class,
            NLPModelLoader::fromClasspath);
        String msg = ex.getMessage();
        assertNotNull(msg, "Exception message should not be null");
        // Message should mention the missing file so the user knows what to download
        assertTrue(msg.contains("en-ner-person.bin"),
            "Message should name the missing model file: " + msg);
    }
}
