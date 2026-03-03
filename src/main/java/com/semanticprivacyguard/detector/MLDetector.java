package com.semanticprivacyguard.detector;

import com.semanticprivacyguard.ml.FeatureExtractor;
import com.semanticprivacyguard.ml.NaiveBayesClassifier;
import com.semanticprivacyguard.ml.TrainingData;
import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIMatch.DetectionSource;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.util.TextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Layer 2 detector — context-aware classification using a Naive Bayes model.
 *
 * <h2>How it disambiguates</h2>
 *
 * <p>The regex layer catches structurally obvious PII (credit cards, SSNs,
 * emails).  But many tokens — especially proper nouns — are ambiguous:</p>
 * <ul>
 *   <li><em>"Apple"</em> in "I ate an <b>Apple</b>" → fruit, benign.</li>
 *   <li><em>"Apple"</em> in "Contact <b>Apple</b> at 555-0199" → company PII.</li>
 *   <li><em>"John"</em> in "the Gospel of <b>John</b>" → biblical reference.</li>
 *   <li><em>"John"</em> in "Dear <b>John</b>, your SSN is…" → person's name.</li>
 * </ul>
 *
 * <p>The ML detector scans every title-cased token (potential name or org) and
 * every high-entropy token (potential key or secret), extracts a context
 * feature vector via {@link FeatureExtractor}, and asks the Naive Bayes
 * classifier whether the context is PII-like or benign.</p>
 *
 * <h2>Confidence threshold</h2>
 *
 * <p>A token is flagged only if the "PII" class posterior probability exceeds
 * the configurable {@code confidenceThreshold} (default {@code 0.65}).
 * Callers can tighten (more precise) or loosen (more recall-oriented) this
 * threshold via the {@link com.semanticprivacyguard.config.SPGConfig} builder.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class MLDetector implements PIIDetector {

    /** Default minimum posterior probability to flag a token as PII. */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.65;

    /** Minimum token length (after stripping trailing punctuation) to consider. */
    private static final int MIN_TOKEN_LENGTH = 3;

    /** Minimum Shannon entropy to consider a token as a potential API key. */
    private static final double KEY_ENTROPY_THRESHOLD = 3.5;

    /**
     * Known preceding keywords that signal the following token is likely a
     * person name or organisation.  Only title-cased tokens whose immediately
     * preceding word (the {@code PREV:} feature) is in this set are sent to the
     * classifier.  This prevents false positives for sentence-starting words
     * like "Hello", "Send", or short abbreviations like "SSN:".
     *
     * <p>High-entropy and alphanumeric-identifier tokens bypass this gate and
     * are always classified regardless of context.</p>
     */
    private static final Set<String> PII_PREV_KEYWORDS = Set.of(
        // Social / honorific titles
        "mr", "mrs", "ms", "miss", "dr", "prof", "professor", "sir", "madam",
        // Greetings
        "dear", "hello", "hi", "hey",
        // Contact / professional context verbs
        "contact", "email", "call", "phone", "reach", "message",
        // Role / title labels
        "manager", "director", "officer", "president", "vp", "ceo", "cto",
        "cfo", "coo", "founder", "owner", "partner", "head",
        // Document / communication metadata
        "signed", "from", "to", "author", "by", "sender", "recipient",
        "sent", "written", "submitted", "approved",
        // Healthcare
        "patient", "client", "customer", "employee", "user",
        // Identity labels
        "name", "fullname", "firstname", "lastname", "surname",
        // Organisation labels
        "company", "corp", "inc", "ltd", "llc", "firm", "vendor",
        "employer", "provider", "contractor", "supplier"
    );

    private final NaiveBayesClassifier classifier;
    private final FeatureExtractor     featureExtractor;
    private final double               confidenceThreshold;

    /**
     * Creates a detector using the built-in training corpus and default threshold.
     */
    public MLDetector() {
        this(TrainingData.buildDefaultClassifier(),
             new FeatureExtractor(),
             DEFAULT_CONFIDENCE_THRESHOLD);
    }

    /**
     * Creates a detector with a custom classifier and threshold.
     *
     * @param classifier          a pre-fitted Naive Bayes classifier
     * @param featureExtractor    feature extraction strategy
     * @param confidenceThreshold PII posterior probability cutoff in (0, 1]
     */
    public MLDetector(NaiveBayesClassifier classifier,
                      FeatureExtractor featureExtractor,
                      double confidenceThreshold) {
        if (!classifier.isFitted()) throw new IllegalArgumentException(
            "Classifier must be fitted before use — call finalizeFit().");
        this.classifier          = classifier;
        this.featureExtractor    = featureExtractor;
        this.confidenceThreshold = confidenceThreshold;
    }

    @Override
    public String name() { return "MLDetector"; }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<PIIMatch> detect(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<int[]>   spans   = TextUtils.tokenSpans(text);
        List<PIIMatch> results = new ArrayList<>();
        List<String>  classes  = classifier.getClasses();
        int           piiIndex = classes.indexOf("PII");

        for (int i = 0; i < spans.size(); i++) {
            int[]  span  = spans.get(i);

            // Strip trailing punctuation from the token span (e.g. "Alice," → "Alice")
            int cleanEnd = span[1];
            while (cleanEnd > span[0] && !Character.isLetterOrDigit(text.charAt(cleanEnd - 1))) {
                cleanEnd--;
            }
            if (cleanEnd <= span[0]) continue;
            String token = text.substring(span[0], cleanEnd);

            if (token.length() < MIN_TOKEN_LENGTH) continue;
            if (!isCandidateToken(token))           continue;

            List<String> features = featureExtractor.extract(text, spans, i);

            // Gate: title-cased tokens require a known PII context keyword as
            // their immediate predecessor.  High-entropy and identifier tokens
            // bypass this gate because their structural signals are sufficient.
            boolean isHighEntropy = TextUtils.shannonEntropy(token) >= KEY_ENTROPY_THRESHOLD;
            boolean isIdentifier  = token.length() >= 4
                    && token.matches("[A-Z]{2,}[0-9]{2,}.*");
            if (!isHighEntropy && !isIdentifier && !hasPIIKeywordSignal(features)) {
                continue;
            }

            double[] proba   = classifier.predictProba(features);
            double   piiProb = piiIndex >= 0 ? proba[piiIndex] : 0.0;

            if (piiProb >= confidenceThreshold) {
                PIIType type = classifyType(token, features);
                results.add(new PIIMatch(
                    type,
                    token,
                    span[0], cleanEnd,
                    DetectionSource.ML,
                    piiProb));
            }
        }
        return results;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Determines whether a token is worth running through the classifier.
     * Cheap filters to avoid classifying every stop word.
     */
    private boolean isCandidateToken(String token) {
        char first = token.charAt(0);

        // Title-cased tokens (potential names / organisations)
        if (Character.isUpperCase(first)) return true;

        // High-entropy tokens (potential API keys / secrets)
        if (TextUtils.shannonEntropy(token) >= KEY_ENTROPY_THRESHOLD) return true;

        // Alphanumeric tokens that look like identifiers (MRN123, EMP-456)
        if (token.length() >= 4
                && token.matches("[A-Z]{2,}[0-9]{2,}.*")) return true;

        return false;
    }

    /**
     * Returns {@code true} if the feature list contains a {@code PREV:keyword}
     * entry whose keyword is in {@link #PII_PREV_KEYWORDS}.
     *
     * <p>Title-cased tokens that have no known PII context keyword immediately
     * before them are very likely benign sentence starters ("Hello", "The",
     * "Send") and should be skipped to avoid false positives.</p>
     */
    private boolean hasPIIKeywordSignal(List<String> features) {
        for (String f : features) {
            if (f.startsWith("PREV:")) {
                String prev = f.substring(5).toLowerCase(Locale.ROOT);
                if (PII_PREV_KEYWORDS.contains(prev)) return true;
            }
        }
        return false;
    }

    /**
     * Maps detected context features to a specific {@link PIIType}.
     * Falls back to {@link PIIType#GENERIC_PII} when the exact type is unclear.
     */
    private PIIType classifyType(String token, List<String> features) {
        String featureStr = String.join(" ", features).toLowerCase();

        if (featureStr.contains("high_entropy")
                || (featureStr.contains("mostly_upper") && token.length() >= 8)) {
            return PIIType.API_KEY;
        }
        if (featureStr.contains("patient") || featureStr.contains("mrn")) {
            return PIIType.MEDICAL_RECORD;
        }
        if (featureStr.contains("company") || featureStr.contains("corp")
                || featureStr.contains("inc") || featureStr.contains("ltd")) {
            return PIIType.ORGANIZATION;
        }
        // Default: title-cased token in PII context → person name
        if (Character.isUpperCase(token.charAt(0))) {
            return PIIType.PERSON_NAME;
        }
        return PIIType.GENERIC_PII;
    }
}
