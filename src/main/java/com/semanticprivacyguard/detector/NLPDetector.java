package com.semanticprivacyguard.detector;

import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.nlp.NLPModelLoader.LoadedModels;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.WhitespaceTokenizer;
import opennlp.tools.util.Span;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <b>PII detector backed by Apache OpenNLP Named Entity Recognition.</b>
 *
 * <p>Supplements the {@link HeuristicDetector} and {@link MLDetector} with
 * a proper Maximum Entropy (MaxEnt) NER model trained on large corpora.
 * OpenNLP understands multi-token person names ({@code "John Michael Smith"}),
 * compound organisation names ({@code "Barclays Bank PLC"}), and
 * names that appear in varied syntactic positions — cases that trip up the
 * bag-of-words Naive Bayes layer.</p>
 *
 * <h2>Detected types</h2>
 * <ul>
 *   <li>{@link PIIType#PERSON_NAME} — from {@code en-ner-person.bin}</li>
 *   <li>{@link PIIType#ORGANIZATION} — from {@code en-ner-organization.bin}
 *       (only if that model was loaded)</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 *
 * <p>{@link NameFinderME} maintains per-call <em>adaptive data</em> that
 * accumulates evidence across tokens in a document.  It is <b>not thread-safe</b>.
 * This class uses {@link ThreadLocal} to give each thread its own
 * {@code NameFinderME} wrapper (initialized lazily from the shared, immutable
 * {@link TokenNameFinderModel}).  Adaptive data is cleared after every
 * {@link #detect} call so no state bleeds between requests.</p>
 *
 * <p>With Java 17+ virtual threads each virtual thread gets its own wrapper
 * instance; since {@code NameFinderME} is lightweight (it holds only adaptive
 * state), the per-thread overhead is negligible.</p>
 *
 * @author Hemant Naik
 * @since 1.1.0
 * @see com.semanticprivacyguard.nlp.NLPModelLoader
 */
public final class NLPDetector implements PIIDetector {

    private final double minProbability;

    // Immutable, thread-safe model objects shared across all threads
    private final TokenNameFinderModel personModel;
    private final TokenNameFinderModel organizationModel; // may be null

    // Thread-local NameFinderME wrappers — one per thread, lazily initialized
    private final ThreadLocal<NameFinderME> personFinder;
    private final ThreadLocal<NameFinderME> organizationFinder; // may produce null
    private final ThreadLocal<TokenizerME>  tokenizerME;        // may produce null

    // Shared immutable WhitespaceTokenizer fallback
    private final boolean useWhitespaceTokenizer;

    /**
     * Creates an {@code NLPDetector} from pre-loaded OpenNLP models.
     *
     * @param models         models loaded via {@link com.semanticprivacyguard.nlp.NLPModelLoader}
     *                       (never {@code null})
     * @param minProbability minimum OpenNLP entity probability to accept as a match;
     *                       entities with probability below this value are discarded
     */
    public NLPDetector(LoadedModels models, double minProbability) {
        Objects.requireNonNull(models,              "models must not be null");
        Objects.requireNonNull(models.personModel(),"person model must not be null");

        this.minProbability    = minProbability;
        this.personModel       = models.personModel();
        this.organizationModel = models.organizationModel();

        // Lazy-init ThreadLocals — each thread creates its own NameFinderME wrapper
        this.personFinder = ThreadLocal.withInitial(
            () -> new NameFinderME(personModel));

        this.organizationFinder = (organizationModel != null)
            ? ThreadLocal.withInitial(() -> new NameFinderME(organizationModel))
            : null;

        this.useWhitespaceTokenizer = (models.tokenizerModel() == null);
        this.tokenizerME = (!useWhitespaceTokenizer)
            ? ThreadLocal.withInitial(() -> new TokenizerME(models.tokenizerModel()))
            : null;
    }

    @Override
    public String name() { return "NLPDetector"; }

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Runs OpenNLP NER on {@code text} and returns all person-name and
     * organisation-name spans that meet the configured probability threshold.
     *
     * <p>Adaptive data is cleared after each call so no state leaks between
     * requests.  The method is safe to call concurrently from many threads.</p>
     *
     * @param text the input text (never {@code null})
     * @return mutable list of PII matches; empty if none found
     */
    @Override
    public List<PIIMatch> detect(String text) {
        if (text == null || text.isBlank()) return List.of();

        // ── Tokenize ──────────────────────────────────────────────────────────
        // tokenPositions[i] = character span of the i-th token in 'text'
        Span[]   tokenPositions;
        String[] tokens;

        if (useWhitespaceTokenizer) {
            tokenPositions = WhitespaceTokenizer.INSTANCE.tokenizePos(text);
            tokens         = Span.spansToStrings(tokenPositions, text);
        } else {
            TokenizerME tok = tokenizerME.get();
            tokenPositions  = tok.tokenizePos(text);
            tokens          = Span.spansToStrings(tokenPositions, text);
        }

        if (tokens.length == 0) return List.of();

        List<PIIMatch> matches = new ArrayList<>();

        // ── Person NER ────────────────────────────────────────────────────────
        NameFinderME pf = personFinder.get();
        try {
            Span[] personSpans = pf.find(tokens);
            collectMatches(personSpans, tokenPositions, text, PIIType.PERSON_NAME, matches);
        } finally {
            pf.clearAdaptiveData();
        }

        // ── Organisation NER (optional) ───────────────────────────────────────
        if (organizationFinder != null) {
            NameFinderME of = organizationFinder.get();
            try {
                Span[] orgSpans = of.find(tokens);
                collectMatches(orgSpans, tokenPositions, text, PIIType.ORGANIZATION, matches);
            } finally {
                of.clearAdaptiveData();
            }
        }

        return matches;
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Maps OpenNLP token-space {@code Span} objects back to character offsets
     * in the original text and creates {@link PIIMatch} instances for each
     * entity that meets {@code minProbability}.
     */
    private void collectMatches(Span[]         entitySpans,
                                 Span[]         tokenPositions,
                                 String         text,
                                 PIIType        type,
                                 List<PIIMatch> out) {
        for (Span entity : entitySpans) {
            if (entity.getProb() < minProbability) continue;

            int startTok = entity.getStart();
            int endTok   = entity.getEnd() - 1;   // entity.getEnd() is exclusive

            // Guard against malformed spans
            if (startTok >= tokenPositions.length || endTok >= tokenPositions.length) continue;

            int charStart = tokenPositions[startTok].getStart();
            int charEnd   = tokenPositions[endTok].getEnd();

            String value = text.substring(charStart, charEnd).trim();
            if (value.isEmpty()) continue;

            out.add(new PIIMatch(
                type,
                value,
                charStart,
                charEnd,
                PIIMatch.DetectionSource.NLP,
                Math.min(entity.getProb(), 1.0)
            ));
        }
    }
}
