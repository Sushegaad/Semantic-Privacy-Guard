package com.semanticprivacyguard.demo;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.model.RedactionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller that demonstrates the SPG privacy-firewall pattern:
 *
 * <pre>
 *   Incoming prompt
 *       │
 *       ▼
 *   ┌─────────────────────────────────────┐
 *   │  1. SPG.redact(prompt)              │  ← PII → [EMAIL_1], [SSN_1] …
 *   │  2. LlmStubService.complete(clean)  │  ← call LLM with sanitised prompt
 *   │  3. SPG.deTokenize(response, map)   │  ← restore originals in the reply
 *   └─────────────────────────────────────┘
 *       │
 *       ▼
 *   Response with real values restored
 * </pre>
 *
 * <h2>Try it</h2>
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/chat \
 *     -H "Content-Type: application/json" \
 *     -d '{
 *       "prompt": "My name is Alice Johnson and my email is alice@corp.com. What is my risk level?"
 *     }'
 * </pre>
 *
 * @author Hemant Naik
 */
@RestController
@RequestMapping("/api")
public class GatewayController {

    private final SemanticPrivacyGuard spg;
    private final LlmStubService       llm;

    public GatewayController(SemanticPrivacyGuard spg, LlmStubService llm) {
        this.spg = spg;
        this.llm = llm;
    }

    /**
     * POST /api/chat
     *
     * <p>Accepts {@code {"prompt": "..."}} and returns:</p>
     * <pre>
     * {
     *   "originalPrompt":   "My name is Alice Johnson ...",
     *   "sanitisedPrompt":  "My name is [PERSON_NAME_1] [PERSON_NAME_2] ...",
     *   "llmResponse":      "Hello [PERSON_NAME_1], your risk level is low.",
     *   "finalResponse":    "Hello Alice, your risk level is low.",
     *   "piiMatchCount":    3,
     *   "redactionTokens":  {"[EMAIL_1]": "alice@corp.com", ...}
     * }
     * </pre>
     */
    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        if (prompt.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "prompt must not be blank"));
        }

        // ── Step 1: Redact PII from the incoming prompt ──────────────────────
        RedactionResult redacted = spg.redact(prompt);
        // Enable reverse map so we can de-tokenize the LLM's reply
        SemanticPrivacyGuard spgWithMap = SemanticPrivacyGuard.create(
                com.semanticprivacyguard.config.SPGConfig.builder()
                        .buildReverseMap(true)
                        .build());
        RedactionResult redactedWithMap = spgWithMap.redact(prompt);

        String sanitisedPrompt = redactedWithMap.getRedactedText();
        Map<String, String> tokenMap = redactedWithMap.getReverseMap();

        // ── Step 2: Call LLM with the sanitised prompt ───────────────────────
        String llmRaw = llm.complete(sanitisedPrompt);

        // ── Step 3: De-tokenize — restore original values in the LLM reply ──
        String finalResponse = deTokenize(llmRaw, tokenMap);

        return ResponseEntity.ok(Map.of(
                "originalPrompt",  prompt,
                "sanitisedPrompt", sanitisedPrompt,
                "llmResponse",     llmRaw,
                "finalResponse",   finalResponse,
                "piiMatchCount",   redactedWithMap.getMatchCount(),
                "redactionTokens", tokenMap
        ));
    }

    /** GET /api/health — liveness probe. */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "version", SemanticPrivacyGuard.VERSION
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Replaces all SPG tokens in {@code text} with the original PII values
     * from the reverse map returned by {@link RedactionResult#getReverseMap()}.
     *
     * <p>In a real implementation, the LLM may echo a token verbatim in its
     * reply (e.g. "Hello [EMAIL_1], here is your summary"). This step
     * restores those tokens to the originals so the end-user sees their
     * real data in the response.</p>
     */
    private static String deTokenize(String text, Map<String, String> tokenMap) {
        String result = text;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
