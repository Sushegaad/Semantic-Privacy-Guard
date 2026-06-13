package com.semanticprivacyguard.filter;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SPGRequestFilter}.
 *
 * @since 1.5.0
 */
class SPGRequestFilterTest {

    private SPGRequestFilter    filter;
    private SPGFilterProperties props;

    @BeforeEach
    void setUp() {
        props  = new SPGFilterProperties();
        filter = new SPGRequestFilter(SemanticPrivacyGuard.create(), props);
    }

    // ── Request body redaction ────────────────────────────────────────────────

    @Test
    @DisplayName("Redacts PII from JSON request body")
    void redactsRequestBody() throws Exception {
        String json = """
                {"name":"John Doe","email":"john.doe@example.com","ssn":"123-45-6789"}
                """;
        MockHttpServletRequest  req   = jsonPost("/api/users", json);
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Verify the filter chain received a request with redacted body
        MockHttpServletRequest downstream =
                (MockHttpServletRequest) chain.getRequest();
        String body = new String(downstream.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertFalse(body.contains("john.doe@example.com"), "email should be redacted");
        assertFalse(body.contains("123-45-6789"),           "SSN should be redacted");
        assertTrue(body.contains("[EMAIL_"),  "should contain EMAIL token");
        assertTrue(body.contains("[SSN_"),    "should contain SSN token");
    }

    @Test
    @DisplayName("Passes through request body unchanged when no PII present")
    void passesCleanRequestBodyUnchanged() throws Exception {
        String json = """
                {"product":"widget","quantity":5}
                """;
        MockHttpServletRequest  req   = jsonPost("/api/orders", json);
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        MockHttpServletRequest downstream =
                (MockHttpServletRequest) chain.getRequest();
        String body = new String(downstream.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertEquals(json.trim(), body.trim());
    }

    // ── Path exclusion ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Skips excluded paths (actuator health)")
    void skipsExcludedPath() throws Exception {
        String json = """
                {"email":"test@example.com"}
                """;
        MockHttpServletRequest  req   = jsonPost("/actuator/health", json);
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // Chain should have received the ORIGINAL request (not a wrapper)
        MockHttpServletRequest downstream =
                (MockHttpServletRequest) chain.getRequest();
        // If exclusion worked, the body is the untouched original
        assertSame(req, downstream, "excluded path should pass through the original request");
    }

    // ── Ant path matching ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Ant ** matches across path segments")
    void antDoubleStarMatchesDeepPaths() {
        assertTrue(SPGRequestFilter.antMatch("/**",           "/api/v1/users"));
        assertTrue(SPGRequestFilter.antMatch("/api/**",       "/api/v1/users/42"));
        assertFalse(SPGRequestFilter.antMatch("/api/**",      "/other/v1/users"));
    }

    @Test
    @DisplayName("Ant * matches within a single segment")
    void antSingleStarMatchesSingleSegment() {
        assertTrue(SPGRequestFilter.antMatch("/api/v*/users", "/api/v1/users"));
        assertFalse(SPGRequestFilter.antMatch("/api/v*/users", "/api/v1/deep/users"));
    }

    @Test
    @DisplayName("Ant ? matches single character")
    void antQuestionMarkMatchesSingleChar() {
        assertTrue(SPGRequestFilter.antMatch("/api/v?/users",  "/api/v1/users"));
        assertTrue(SPGRequestFilter.antMatch("/api/v?/users",  "/api/v2/users"));
        assertFalse(SPGRequestFilter.antMatch("/api/v?/users", "/api/v10/users"));
    }

    // ── Disabled filter ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Non-JSON content type is passed through without redaction")
    void nonJsonContentTypePassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/upload");
        req.setContentType("multipart/form-data");
        req.setContent(new byte[]{1, 2, 3});

        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        // No wrapper should have been applied — chain gets the original request
        assertSame(req, chain.getRequest());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MockHttpServletRequest jsonPost(String path, String body) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", path);
        req.setContentType("application/json;charset=UTF-8");
        req.setCharacterEncoding("UTF-8");
        req.setContent(body.getBytes(StandardCharsets.UTF_8));
        return req;
    }
}
