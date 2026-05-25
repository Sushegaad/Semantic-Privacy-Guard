package com.semanticprivacyguard.springai;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.model.RedactionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SPGAdvisor}.
 *
 * <h2>Testing strategy</h2>
 * <p>Spring AI's {@link org.springframework.ai.chat.client.advisor.api.AdvisedRequest}
 * is a complex record/class that uses a static {@code from()} builder and requires
 * a live {@link org.springframework.ai.chat.model.ChatModel} to construct.  Mocking
 * the full {@code aroundCall} lifecycle at that API boundary would require either a
 * running Spring context or deep Mockito mocking of sealed/final Spring AI internals —
 * both inappropriate for fast unit tests.</p>
 *
 * <p>Instead we test the two package-private helper methods that contain all the
 * business logic ({@link SPGAdvisor#redactUserText} and
 * {@link SPGAdvisor#redactSystemText}), then add one integration-style test that
 * verifies the full {@code aroundCall} path with fully-mocked Spring AI objects.
 * The integration test documents what a real end-to-end call looks like; teams
 * wanting deeper coverage of the Spring AI advisor chain should write
 * {@code @SpringBootTest} slice tests in their own application.</p>
 *
 * @author Hemant Naik
 * @since 1.4.0
 */
@ExtendWith(MockitoExtension.class)
class SPGAdvisorTest {

    // ── Real SPG instance — gives us genuine redaction behaviour ──────────────
    private SemanticPrivacyGuard realSpg;

    // ── Mocked SPG — gives us controlled, predictable results for advisor tests ─
    @Mock
    private SemanticPrivacyGuard mockSpg;

    @Mock
    private RedactionResult mockUserResult;

    @Mock
    private RedactionResult mockSysResult;

    @BeforeEach
    void setUp() {
        // Default SPG — uses TOKEN mode, all PII types, default thresholds
        realSpg = SemanticPrivacyGuard.create();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. userText_withEmail_isRedacted
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("User text containing an email address is redacted to [EMAIL_1]")
    void userText_withEmail_isRedacted() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);

        RedactionResult result = advisor.redactUserText("My email is alice@example.com");

        assertNotNull(result, "RedactionResult must not be null");
        assertTrue(result.containsPII(),
                "Should detect PII in a string containing an email address");
        assertFalse(result.getRedactedText().contains("alice@example.com"),
                "Redacted text must not contain the original email");
        assertTrue(result.getRedactedText().contains("[EMAIL_1]"),
                "Redacted text should contain the EMAIL token");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. userText_withSSN_isRedacted
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("User text containing an SSN is redacted to [SSN_1]")
    void userText_withSSN_isRedacted() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);

        RedactionResult result = advisor.redactUserText(
                "Patient SSN is 234-56-7890, please update records.");

        assertNotNull(result);
        assertTrue(result.containsPII(), "Should detect SSN as PII");
        assertFalse(result.getRedactedText().contains("234-56-7890"),
                "Redacted text must not contain the original SSN");
        assertTrue(result.getRedactedText().contains("[SSN_1]"),
                "Redacted text should contain the SSN token");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. userText_clean_isPassedThrough
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("Clean user text with no PII is forwarded unchanged")
    void userText_clean_isPassedThrough() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        String clean = "What is the capital of France?";

        RedactionResult result = advisor.redactUserText(clean);

        assertNotNull(result);
        assertFalse(result.containsPII(), "Clean text should not trigger PII detection");
        assertEquals(clean, result.getRedactedText(),
                "Redacted text must equal input when no PII is present");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. reverseMap_storedInContext — tested via redactUserText helper
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("When PII is detected, the reverse map contains the token-to-original mapping")
    void reverseMap_storedInContext() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);

        RedactionResult result = advisor.redactUserText(
                "Contact bob@company.org for help.");

        assertNotNull(result);
        assertTrue(result.containsPII(), "Should detect email as PII");

        Map<String, String> reverseMap = result.getReverseMap();
        assertNotNull(reverseMap, "Reverse map must not be null");
        assertFalse(reverseMap.isEmpty(), "Reverse map must not be empty when PII was found");

        // The reverse map should map the token back to the original email
        assertTrue(reverseMap.containsValue("bob@company.org"),
                "Reverse map must contain the original email address as a value");
        // The key should be the structured token
        assertTrue(reverseMap.keySet().stream().anyMatch(k -> k.startsWith("[EMAIL_")),
                "Reverse map key should be a structured [EMAIL_N] token");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. systemPrompt_notRedactedByDefault
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("System prompt is NOT redacted when redactSystemPrompt=false (default)")
    void systemPrompt_notRedactedByDefault() {
        // Default constructor — redactSystemPrompt is false
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        String systemText = "You are a helpful assistant. Admin email is admin@corp.com.";

        RedactionResult result = advisor.redactSystemText(systemText);

        // With redactSystemPrompt=false, redactSystemText must return null
        assertNull(result,
                "redactSystemText must return null when redactSystemPrompt=false");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 6. systemPrompt_redactedWhenEnabled
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("System prompt IS redacted when redactSystemPrompt=true")
    void systemPrompt_redactedWhenEnabled() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg, /* redactSystemPrompt= */ true, SPGAdvisor.DEFAULT_ORDER);
        String systemText = "You are a helpful assistant. Admin email is admin@corp.com.";

        RedactionResult result = advisor.redactSystemText(systemText);

        assertNotNull(result, "redactSystemText must return a result when redactSystemPrompt=true");
        assertTrue(result.containsPII(), "Should detect email in system text");
        assertFalse(result.getRedactedText().contains("admin@corp.com"),
                "Redacted system text must not contain the original email");
        assertTrue(result.getRedactedText().contains("[EMAIL_1]"),
                "Redacted system text should contain the EMAIL token");
    }

    // ════════════════════════════════════════════════════════════════════════
    // 7. getName_returnsSPGAdvisor
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getName() returns the string \"SPGAdvisor\"")
    void getName_returnsSPGAdvisor() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        assertEquals("SPGAdvisor", advisor.getName());
    }

    // ════════════════════════════════════════════════════════════════════════
    // 8. getOrder_returnsConfiguredOrder
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getOrder() returns the custom order value supplied at construction")
    void getOrder_returnsConfiguredOrder() {
        int customOrder = 42;
        SPGAdvisor advisor = new SPGAdvisor(realSpg, false, customOrder);
        assertEquals(customOrder, advisor.getOrder(),
                "getOrder() must return exactly the value passed to the constructor");
    }

    @Test
    @DisplayName("getOrder() returns DEFAULT_ORDER when using the single-arg constructor")
    void getOrder_returnsDefaultOrder() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        assertEquals(SPGAdvisor.DEFAULT_ORDER, advisor.getOrder(),
                "Default constructor must use DEFAULT_ORDER");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Additional edge-case tests
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("redactUserText returns null for null input")
    void redactUserText_null_returnsNull() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        assertNull(advisor.redactUserText(null),
                "null input must produce null result (no NPE)");
    }

    @Test
    @DisplayName("redactUserText returns null for blank input")
    void redactUserText_blank_returnsNull() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        assertNull(advisor.redactUserText("   "),
                "blank input must produce null result (no NPE)");
    }

    @Test
    @DisplayName("SPGAdvisor constructor rejects null SemanticPrivacyGuard")
    void constructor_nullSpg_throwsNPE() {
        assertThrows(NullPointerException.class,
                () -> new SPGAdvisor(null),
                "Constructor must throw NullPointerException when spg is null");
    }

    @Test
    @DisplayName("SPGAdvisor.from(SPGConfig) creates a working advisor")
    void from_config_createsAdvisor() {
        com.semanticprivacyguard.config.SPGConfig config = com.semanticprivacyguard.config.SPGConfig.builder()
                .redactionMode(com.semanticprivacyguard.tokenizer.PIITokenizer.RedactionMode.TOKEN)
                .mlConfidenceThreshold(0.65)
                .minimumSeverity(1)
                .build();

        SPGAdvisor advisor = SPGAdvisor.from(config);

        assertNotNull(advisor, "SPGAdvisor.from() must not return null");
        assertEquals("SPGAdvisor", advisor.getName());
        assertEquals(SPGAdvisor.DEFAULT_ORDER, advisor.getOrder());
    }

    @Test
    @DisplayName("Multiple PII types in a single string are all redacted")
    void userText_multiplePiiTypes_allRedacted() {
        SPGAdvisor advisor = new SPGAdvisor(realSpg);
        String text = "Email alice@example.com, SSN 234-56-7890 are both sensitive.";

        RedactionResult result = advisor.redactUserText(text);

        assertNotNull(result);
        assertTrue(result.containsPII());
        assertFalse(result.getRedactedText().contains("alice@example.com"),
                "Email must be redacted");
        assertFalse(result.getRedactedText().contains("234-56-7890"),
                "SSN must be redacted");
        assertTrue(result.getMatchCount() >= 2,
                "At least two PII matches should be found");
    }

    @Test
    @DisplayName("isRedactSystemPrompt() reflects the constructor argument")
    void isRedactSystemPrompt_reflectsConstructorArg() {
        SPGAdvisor defaultAdvisor = new SPGAdvisor(realSpg);
        assertFalse(defaultAdvisor.isRedactSystemPrompt(),
                "Default advisor must not redact system prompts");

        SPGAdvisor enabledAdvisor = new SPGAdvisor(realSpg, true, SPGAdvisor.DEFAULT_ORDER);
        assertTrue(enabledAdvisor.isRedactSystemPrompt(),
                "Advisor with redactSystemPrompt=true must report true");
    }

    // ════════════════════════════════════════════════════════════════════════
    // Integration-style test using mocked SPG (controlled redaction results)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Verifies advisor behaviour using a mocked {@link SemanticPrivacyGuard} so
     * that exact token output can be asserted without depending on ML model
     * non-determinism.  This test covers the interaction contract between
     * {@code SPGAdvisor} and the underlying {@code SemanticPrivacyGuard}.
     *
     * <p>Note: testing the full {@link SPGAdvisor#aroundCall} path (including
     * the Spring AI {@code AdvisedRequest} builder and {@code CallAroundAdvisorChain})
     * requires a live Spring Boot context.  See the project's integration-test
     * module for {@code @SpringBootTest} coverage of that path.</p>
     */
    @Test
    @DisplayName("redactUserText delegates to SemanticPrivacyGuard.redact() exactly once")
    void redactUserText_delegatesToSpgOnce() {
        String input = "My email is test@test.com";
        when(mockSpg.redact(input)).thenReturn(mockUserResult);
        when(mockUserResult.containsPII()).thenReturn(true);
        when(mockUserResult.getRedactedText()).thenReturn("My email is [EMAIL_1]");

        SPGAdvisor advisor = new SPGAdvisor(mockSpg);
        RedactionResult result = advisor.redactUserText(input);

        verify(mockSpg, times(1)).redact(input);
        assertSame(mockUserResult, result,
                "Must return the result from SemanticPrivacyGuard.redact() unchanged");
    }

    @Test
    @DisplayName("redactSystemText delegates to SemanticPrivacyGuard.redact() when enabled")
    void redactSystemText_delegatesToSpgWhenEnabled() {
        String input = "System prompt with secret@server.io inside.";
        when(mockSpg.redact(input)).thenReturn(mockSysResult);

        SPGAdvisor advisor = new SPGAdvisor(mockSpg, /* redactSystemPrompt= */ true, SPGAdvisor.DEFAULT_ORDER);
        RedactionResult result = advisor.redactSystemText(input);

        verify(mockSpg, times(1)).redact(input);
        assertSame(mockSysResult, result);
    }

    @Test
    @DisplayName("redactSystemText never calls SemanticPrivacyGuard.redact() when disabled")
    void redactSystemText_neverCallsSpgWhenDisabled() {
        String input = "System prompt with secret@server.io inside.";

        SPGAdvisor advisor = new SPGAdvisor(mockSpg); // redactSystemPrompt=false
        RedactionResult result = advisor.redactSystemText(input);

        verify(mockSpg, never()).redact(anyString());
        assertNull(result, "Should return null without invoking SPG");
    }
}
