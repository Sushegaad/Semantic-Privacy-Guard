package com.semanticprivacyguard.ml;

import java.util.*;

/**
 * Multinomial Naive Bayes classifier — pure Java, zero external dependencies.
 *
 * <h2>Mathematical Foundation</h2>
 *
 * <p>For a feature vector {@code x = (x₁, …, xₙ)} and class {@code c}:</p>
 * <pre>
 *   P(c | x) ∝ P(c) · ∏ P(xᵢ | c)
 * </pre>
 *
 * <p>All probabilities are stored in log-space to avoid floating-point
 * underflow on long feature vectors:</p>
 * <pre>
 *   log P(c | x) ∝ log P(c) + Σ log P(xᵢ | c)
 * </pre>
 *
 * <p>Laplace smoothing (α = 1.0) is applied during training to handle
 * features that appear in test data but were absent from training.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * NaiveBayesClassifier clf = new NaiveBayesClassifier();
 * clf.train("PII",    List.of("contact", "john", "at", "555"));
 * clf.train("BENIGN", List.of("i", "ate", "an", "apple"));
 * clf.finalize(); // must call before predict()
 *
 * String label = clf.predict(List.of("email", "alice", "at", "acme"));
 * double[] probs = clf.predictProba(List.of("email", "alice", "at", "acme"));
 * }</pre>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class NaiveBayesClassifier {

    /** Laplace smoothing parameter α. */
    private static final double ALPHA = 1.0;

    // ── Training state ────────────────────────────────────────────────────────

    /** Training-time document counts per class. */
    private final Map<String, Integer> classDocCount = new LinkedHashMap<>();

    /** Training-time word counts per class: class → word → count. */
    private final Map<String, Map<String, Integer>> classWordCount = new LinkedHashMap<>();

    /** Set of all distinct words seen during training. */
    private final Set<String> vocabulary = new HashSet<>();

    private int totalDocCount = 0;

    // ── Fitted parameters (populated by finalizeFit) ──────────────────────────

    private Map<String, Double>              logPriors;
    private Map<String, Map<String, Double>> logLikelihoods;
    private List<String>                     classes;
    private boolean                          fitted = false;

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Adds one training example.
     *
     * @param label    the class label (e.g. {@code "PII"}, {@code "BENIGN"})
     * @param features list of string features (bag-of-words tokens)
     */
    public void train(String label, List<String> features) {
        if (fitted) throw new IllegalStateException(
            "Cannot train after finalizeFit() has been called.");
        classDocCount.merge(label, 1, Integer::sum);
        Map<String, Integer> wc = classWordCount
            .computeIfAbsent(label, k -> new HashMap<>());
        for (String f : features) {
            String lf = f.toLowerCase(Locale.ROOT);
            wc.merge(lf, 1, Integer::sum);
            vocabulary.add(lf);
        }
        totalDocCount++;
    }

    /**
     * Finalises training: computes log-priors and log-likelihoods with Laplace
     * smoothing.  Must be called exactly once before {@link #predict}.
     */
    public void finalizeFit() {
        if (totalDocCount == 0) throw new IllegalStateException(
            "No training examples provided.");
        classes        = new ArrayList<>(classDocCount.keySet());
        logPriors      = new HashMap<>();
        logLikelihoods = new HashMap<>();
        int vocabSize  = vocabulary.size();

        for (String cls : classes) {
            // log prior
            double prior = (double) classDocCount.getOrDefault(cls, 0)
                         / totalDocCount;
            logPriors.put(cls, Math.log(prior));

            // log likelihood with Laplace smoothing
            Map<String, Integer> wc = classWordCount.getOrDefault(cls,
                                          Collections.emptyMap());
            long totalWords = wc.values().stream().mapToLong(i -> i).sum();
            double denom    = totalWords + ALPHA * vocabSize;

            Map<String, Double> ll = new HashMap<>();
            for (String word : vocabulary) {
                double count = wc.getOrDefault(word, 0) + ALPHA;
                ll.put(word, Math.log(count / denom));
            }
            // store the smoothed probability for unseen words
            ll.put("__UNSEEN__", Math.log(ALPHA / denom));
            logLikelihoods.put(cls, ll);
        }
        fitted = true;
    }

    /**
     * Returns the most likely class label for the given features.
     *
     * @param features the feature tokens (bag-of-words)
     * @return the winning class label
     * @throws IllegalStateException if {@link #finalizeFit()} has not been called
     */
    public String predict(List<String> features) {
        double[] scores = logScores(features);
        int best = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[best]) best = i;
        }
        return classes.get(best);
    }

    /**
     * Returns a probability distribution over all classes for the given
     * features.  Probabilities are normalised via log-sum-exp.
     *
     * @param features the feature tokens (bag-of-words)
     * @return array of probabilities, in the same order as {@link #getClasses()}
     */
    public double[] predictProba(List<String> features) {
        double[] logS = logScores(features);
        // log-sum-exp for numerical stability
        double maxLog = Arrays.stream(logS).max().orElse(0.0);
        double sumExp = 0.0;
        for (double s : logS) sumExp += Math.exp(s - maxLog);
        double logNorm = maxLog + Math.log(sumExp);

        double[] proba = new double[logS.length];
        for (int i = 0; i < logS.length; i++) {
            proba[i] = Math.exp(logS[i] - logNorm);
        }
        return proba;
    }

    /**
     * Returns the ordered list of class labels that correspond to the
     * probability array returned by {@link #predictProba}.
     */
    public List<String> getClasses() {
        ensureFitted();
        return Collections.unmodifiableList(classes);
    }

    /**
     * Returns whether the classifier has been fitted.
     */
    public boolean isFitted() { return fitted; }

    // ── Private ───────────────────────────────────────────────────────────────

    private double[] logScores(List<String> features) {
        ensureFitted();
        double[] scores = new double[classes.size()];
        for (int i = 0; i < classes.size(); i++) {
            String cls = classes.get(i);
            double score = logPriors.get(cls);
            Map<String, Double> ll = logLikelihoods.get(cls);
            for (String f : features) {
                String lf = f.toLowerCase(Locale.ROOT);
                score += ll.getOrDefault(lf,
                             ll.getOrDefault("__UNSEEN__", Math.log(ALPHA)));
            }
            scores[i] = score;
        }
        return scores;
    }

    private void ensureFitted() {
        if (!fitted) throw new IllegalStateException(
            "Classifier not fitted — call finalizeFit() first.");
    }
}
