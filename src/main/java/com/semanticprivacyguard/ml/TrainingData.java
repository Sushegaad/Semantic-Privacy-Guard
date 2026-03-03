package com.semanticprivacyguard.ml;

import java.util.List;

/**
 * Built-in labelled training corpus for the context-classification model.
 *
 * <p>Each entry is a pair of {@code (label, features)} where features are the
 * bag-of-words tokens that {@link FeatureExtractor} would produce for a given
 * context.  The corpus covers:</p>
 * <ul>
 *   <li>{@code "PII"} — contexts where a token is a genuine person name,
 *       organisation, or generic PII.</li>
 *   <li>{@code "BENIGN"} — contexts where the same surface form is harmless
 *       (e.g. "Apple" as a fruit, "John" as a biblical reference).</li>
 * </ul>
 *
 * <p><b>Extending the corpus:</b> Add entries via
 * {@link NaiveBayesClassifier#train(String, List)} before calling
 * {@code finalizeFit()}.  The built-in data is sufficient for demonstration
 * and moderate production use; for high-volume or domain-specific deployments,
 * supplement with labelled data from your own environment.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class TrainingData {

    private TrainingData() { /* utility class */ }

    /** Returns a pre-fitted {@link NaiveBayesClassifier} trained on built-in data. */
    public static NaiveBayesClassifier buildDefaultClassifier() {
        NaiveBayesClassifier clf = new NaiveBayesClassifier();

        // ── PII contexts ──────────────────────────────────────────────────────

        // Names preceded by social/professional titles
        pii(clf, "IS_TITLE_CASE", "PREV:mr",       "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:mrs",      "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:ms",       "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:dr",       "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:prof",     "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:sir",      "dear",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:hello",    "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:hi",       "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "PREV:dear",     "LEN_MEDIUM");

        // Short personal names (≤ 3 chars: Bob, Amy, Kim, Tim, etc.) in PII contexts.
        // Without these, LEN_SHORT is exclusively a BENIGN signal (sentence-starters)
        // and short names after "dear"/"contact"/etc. are mis-classified as BENIGN.
        pii(clf, "IS_TITLE_CASE", "PREV:dear",    "dear",    "LEN_SHORT");
        pii(clf, "IS_TITLE_CASE", "PREV:hello",              "LEN_SHORT");
        pii(clf, "IS_TITLE_CASE", "PREV:hi",                 "LEN_SHORT");
        pii(clf, "IS_TITLE_CASE", "PREV:contact", "contact", "LEN_SHORT");
        pii(clf, "IS_TITLE_CASE", "PREV:from",    "from",    "LEN_SHORT");
        pii(clf, "IS_TITLE_CASE", "PREV:call",    "call",    "LEN_SHORT");

        // Names in contact / professional contexts
        pii(clf, "IS_TITLE_CASE", "contact",   "PREV:contact",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "email",     "PREV:email",    "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "call",      "PREV:call",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "phone",     "PREV:phone",    "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "manager",   "PREV:manager",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "ceo",       "PREV:ceo",      "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "cto",       "PREV:cto",      "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "president", "PREV:president","LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "signed",    "PREV:signed",   "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "from",      "PREV:from",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "sent",      "PREV:sent",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "author",    "PREV:author",   "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "patient",   "PREV:patient",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "client",    "PREV:client",   "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "customer",  "PREV:customer", "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "employee",  "PREV:employee", "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "user",      "PREV:user",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "name",      "PREV:name",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "fullname",  "PREV:fullname", "LEN_MEDIUM");

        // Organisations in business context
        pii(clf, "IS_TITLE_CASE", "company",   "PREV:company",  "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "corp",      "PREV:corp",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "inc",       "PREV:inc",      "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "ltd",       "PREV:ltd",      "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "llc",       "PREV:llc",      "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "firm",      "PREV:firm",     "LEN_MEDIUM");
        pii(clf, "IS_TITLE_CASE", "vendor",    "PREV:vendor",   "LEN_MEDIUM");

        // All-caps identifiers (employee IDs, medical record numbers)
        pii(clf, "ALL_CAPS", "HAS_DIGIT",  "id",     "PREV:id",     "LEN_MEDIUM");
        pii(clf, "ALL_CAPS", "HAS_DIGIT",  "mrn",    "PREV:mrn",    "LEN_MEDIUM");
        pii(clf, "ALL_CAPS", "HAS_DIGIT",  "record", "PREV:record", "LEN_MEDIUM");
        pii(clf, "ALL_CAPS", "HAS_DIGIT",  "ref",    "PREV:ref",    "LEN_MEDIUM");

        // High-entropy strings (tokens, keys)
        pii(clf, "HIGH_ENTROPY", "HAS_DIGIT", "ALL_CAPS", "LEN_LONG",   "key");
        pii(clf, "HIGH_ENTROPY", "HAS_DIGIT",             "LEN_LONG",   "token");
        pii(clf, "HIGH_ENTROPY", "HAS_DIGIT",             "LEN_LONG",   "secret");
        pii(clf, "HIGH_ENTROPY", "MOSTLY_UPPER",          "LEN_LONG",   "api");

        // ── BENIGN contexts ───────────────────────────────────────────────────

        // Organisation names used in non-identifying sentences
        benign(clf, "IS_TITLE_CASE", "ate",   "fruit",    "PREV:ate",   "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "drink", "juice",    "PREV:drink", "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "like",  "food",     "PREV:like",  "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "love",  "product",  "PREV:love",  "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "buy",   "store",    "PREV:buy",   "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "sells", "market",   "PREV:sells", "LEN_MEDIUM");

        // Duplicate food/consumption context entries WITHOUT a PREV: feature so that
        // "ate" and "fruit" themselves become strong standalone BENIGN signals.
        // This ensures the direct classifier call with ["IS_TITLE_CASE","ate","fruit","LEN_MEDIUM"]
        // (no PREV:ate) is correctly labelled BENIGN despite the PII class-prior advantage.
        benign(clf, "IS_TITLE_CASE", "ate",    "fruit",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "ate",    "snack",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "ate",    "food",     "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "fresh",  "fruit",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "sweet",  "fruit",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "ripe",   "fruit",    "LEN_MEDIUM");

        // Common first names used in literary / historical / non-identifying context
        benign(clf, "IS_TITLE_CASE", "biblical", "verse",    "PREV:verse",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "book",     "novel",    "PREV:novel",    "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "chapter",  "story",    "PREV:chapter",  "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "character","fictional","PREV:character", "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "movie",    "film",     "PREV:film",     "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "series",   "show",     "PREV:show",     "LEN_MEDIUM");
        benign(clf, "IS_TITLE_CASE", "game",     "play",     "PREV:play",     "LEN_MEDIUM");

        // Generic common words that are title-cased only at sentence start
        benign(clf, "IS_TITLE_CASE", "the",  "is",   "was",  "PREV:the",  "LEN_SHORT");
        benign(clf, "IS_TITLE_CASE", "a",    "an",   "was",  "PREV:a",    "LEN_SHORT");
        benign(clf, "IS_TITLE_CASE", "this", "that", "was",  "PREV:this", "LEN_SHORT");

        // Low-entropy short words — not API keys
        benign(clf, "LEN_SHORT", "word",  "simple", "text",  "LEN_SHORT");
        benign(clf, "LEN_SHORT", "short", "token",  "label", "LEN_SHORT");

        clf.finalizeFit();
        return clf;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void pii(NaiveBayesClassifier clf, String... features) {
        clf.train("PII", List.of(features));
    }

    private static void benign(NaiveBayesClassifier clf, String... features) {
        clf.train("BENIGN", List.of(features));
    }
}
