package com.semanticprivacyguard.springai;

import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.config.SPGConfig;
import com.semanticprivacyguard.model.RedactionResult;
import org.springframework.ai.chat.client.advisor.api.AdvisedRequest;
import org.springframework.ai.chat.client.advisor.api.AdvisedResponse;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAroundAdvisorChain;
import org.springframework.core.Ordered;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Spring AI {@link CallAroundAdvisor} that redacts PII from prompts before
 * they are sent to any LLM, using Semantic Privacy Guard.
 *
 * <h2>Usage — three lines</h2>
 * <pre>{@code
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultAdvisors(new SPGAdvisor(SemanticPrivacyGuard.create()))
 *     .build();
 * }</pre>
 *
 * <h2>De-tokenization</h2>
 * <p>The reverse map (token → original value) is stored in the
 * {@link AdvisedRequest} context under key {@value #REVERSE_MAP_CONTEXT_KEY}.
 * Application code can retrieve it from the context and call
 * {@code spg.detokenize(responseText, reverseMap)} once de-tokenization
 * is available (planned for v1.5.0).</p>
 *
 * <h2>Thread safety</h2>
 * <p>{@link SemanticPrivacyGuard} is stateless; this advisor is safe to share
 * across threads and virtual threads (Project Loom).</p>
 *
 * @author Hemant Naik
 * @since 1.4.0
 */
public final class SPGAdvisor implements CallAroundAdvisor {

    /** Context key under which the reverse-map is stored for de-tokenization. */
    public static final String REVERSE_MAP_CONTEXT_KEY = "spg.reverseMap";

    /** Default advisor order — runs before most other advisors. */
    public static final int DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private final SemanticPrivacyGuard spg;
    private final boolean              redactSystemPrompt;
    private final int                  order;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates an advisor with a pre-configured {@link SemanticPrivacyGuard}
     * instance, using default settings (system prompt not redacted, default order).
     *
     * @param spg the guard instance to use; must not be {@code null}
     */
    public SPGAdvisor(SemanticPrivacyGuard spg) {
        this(spg, false, DEFAULT_ORDER);
    }

    /**
     * Creates an advisor with full control over all options.
     *
     * @param spg                the guard instance; must not be {@code null}
     * @param redactSystemPrompt {@code true} to also redact the system prompt;
     *                           default {@code false}
     * @param order              advisor chain ordering value; lower = earlier
     */
    public SPGAdvisor(SemanticPrivacyGuard spg, boolean redactSystemPrompt, int order) {
        this.spg                = Objects.requireNonNull(spg, "spg must not be null");
        this.redactSystemPrompt = redactSystemPrompt;
        this.order              = order;
    }

    /**
     * Convenience factory — creates an advisor from a {@link SPGConfig}.
     *
     * @param config the configuration; must not be {@code null}
     * @return a new {@code SPGAdvisor} backed by a guard built from {@code config}
     */
    public static SPGAdvisor from(SPGConfig config) {
        return new SPGAdvisor(SemanticPrivacyGuard.create(config));
    }

    // ── CallAroundAdvisor ─────────────────────────────────────────────────────

    @Override
    public String getName() { return "SPGAdvisor"; }

    @Override
    public int getOrder() { return order; }

    /**
     * Intercepts the request, redacts PII from the user text (and optionally
     * the system prompt), then forwards the sanitised request to the next
     * advisor or model in the chain.
     *
     * <p>The reverse map from the user-text redaction is stored in
     * {@link AdvisedRequest#adviseContext()} under {@value #REVERSE_MAP_CONTEXT_KEY}
     * so that downstream components can de-tokenize the LLM response.</p>
     *
     * @param advisedRequest the incoming request; must not be {@code null}
     * @param chain          the advisor chain; must not be {@code null}
     * @return the {@link AdvisedResponse} from the LLM (response is not modified
     *         in this version — de-tokenization planned for v1.5.0)
     */
    @Override
    public AdvisedResponse aroundCall(AdvisedRequest advisedRequest,
                                      CallAroundAdvisorChain chain) {

        // ── 1. Redact user text ───────────────────────────────────────────────
        String userText = advisedRequest.userText();
        RedactionResult userResult = null;

        if (userText != null && !userText.isBlank()) {
            userResult = spg.redact(userText);
        }

        // ── 2. Optionally redact system prompt ────────────────────────────────
        String systemText = advisedRequest.systemText();
        RedactionResult sysResult = null;

        if (redactSystemPrompt && systemText != null && !systemText.isBlank()) {
            sysResult = spg.redact(systemText);
        }

        // ── 3. Propagate reverse map via advise context ───────────────────────
        Map<String, Object> context = new HashMap<>(advisedRequest.adviseContext());
        if (userResult != null && userResult.containsPII()) {
            context.put(REVERSE_MAP_CONTEXT_KEY, userResult.getReverseMap());
        }

        // ── 4. Build the sanitised request ────────────────────────────────────
        AdvisedRequest.Builder builder = AdvisedRequest.from(advisedRequest);

        if (userResult != null) {
            builder = builder.userText(userResult.getRedactedText());
        }
        if (sysResult != null) {
            builder = builder.systemText(sysResult.getRedactedText());
        }
        builder = builder.adviseContext(context);

        AdvisedRequest sanitised = builder.build();

        // ── 5. Forward to next advisor / LLM ─────────────────────────────────
        AdvisedResponse response = chain.nextAroundCall(sanitised);

        // TODO (v1.5.0): de-tokenize response text using the stored reverse map
        // once SemanticPrivacyGuard.detokenize() is available.

        return response;
    }

    // ── Package-private helpers (used by unit tests) ──────────────────────────

    /**
     * Redacts PII from the given user text and returns the result.
     *
     * <p>Exposed as package-private to allow unit tests to verify redaction
     * behaviour without having to wire the full Spring AI advisor chain.</p>
     *
     * @param text the raw user text; may be {@code null} or blank
     * @return {@link RedactionResult} from SPG, or {@code null} if {@code text}
     *         is {@code null} or blank
     */
    RedactionResult redactUserText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return spg.redact(text);
    }

    /**
     * Redacts PII from the given system text and returns the result.
     *
     * <p>Returns {@code null} when {@link #redactSystemPrompt} is {@code false}
     * (the default) or when {@code text} is {@code null} / blank. Exposed as
     * package-private for unit testing.</p>
     *
     * @param text the raw system prompt; may be {@code null} or blank
     * @return {@link RedactionResult}, or {@code null} when redaction is disabled
     *         or input is empty
     */
    RedactionResult redactSystemText(String text) {
        if (!redactSystemPrompt || text == null || text.isBlank()) {
            return null;
        }
        return spg.redact(text);
    }

    /**
     * Returns whether this advisor is configured to redact the system prompt.
     *
     * @return {@code true} if system-prompt redaction is active
     */
    boolean isRedactSystemPrompt() {
        return redactSystemPrompt;
    }
}
