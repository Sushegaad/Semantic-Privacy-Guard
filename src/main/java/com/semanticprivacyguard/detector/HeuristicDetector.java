package com.semanticprivacyguard.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.semanticprivacyguard.model.PIIMatch;
import com.semanticprivacyguard.model.PIIMatch.DetectionSource;
import com.semanticprivacyguard.model.PIIType;
import com.semanticprivacyguard.util.RegexPatterns;
import com.semanticprivacyguard.util.TextUtils;

/**
 * Layer 1 detector — pattern-based heuristics using the compiled regexes in
 * {@link RegexPatterns}.
 *
 * <p>The detector is entirely stateless and therefore inherently thread-safe.
 * Detection order mirrors sensitivity: highest-impact types (SSN, credit card)
 * are checked first so they can be removed before the ML layer re-scans for
 * subtler signals.</p>
 *
 * <h2>Post-match validation</h2>
 * <ul>
 *   <li>Credit cards are subject to Luhn checksum validation.</li>
 *   <li>Phone numbers require exactly 10 significant digits.</li>
 *   <li>API keys below a Shannon entropy threshold are discarded.</li>
 * </ul>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class HeuristicDetector implements PIIDetector {

    /** Minimum Shannon entropy for a hex/base64 string to be flagged as an API key. */
    private static final double API_KEY_MIN_ENTROPY = 3.5;

    /** PII types to include; if empty, all types are included. */
    private final Set<PIIType> enabledTypes;

    /** Creates a detector that checks all PII types. */
    public HeuristicDetector() {
        this.enabledTypes = Set.of(); // empty = all enabled
    }

    /**
     * Creates a detector that checks only the specified types.
     *
     * @param enabledTypes subset of types to check; must not be {@code null}
     */
    public HeuristicDetector(Set<PIIType> enabledTypes) {
        this.enabledTypes = Set.copyOf(enabledTypes);
    }

    @Override
    public String name() { return "HeuristicDetector"; }

    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public List<PIIMatch> detect(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<PIIMatch> results = new ArrayList<>();

        if (isEnabled(PIIType.SSN))          detectSSN(text, results);
        if (isEnabled(PIIType.CREDIT_CARD))  detectCreditCard(text, results);
        if (isEnabled(PIIType.EMAIL))        detectEmail(text, results);
        if (isEnabled(PIIType.PHONE))        detectPhone(text, results);
        if (isEnabled(PIIType.IP_ADDRESS))   detectIpAddresses(text, results);
        if (isEnabled(PIIType.API_KEY))      detectApiKey(text, results);
        if (isEnabled(PIIType.PASSWORD))     detectPassword(text, results);
        if (isEnabled(PIIType.DATE_OF_BIRTH)) detectDob(text, results);
        if (isEnabled(PIIType.COORDINATES)) detectCoordinates(text, results);
        if (isEnabled(PIIType.BANK_ACCOUNT)) detectBankNumbers(text, results);

        return results;
    }

    // ── Private detection helpers ─────────────────────────────────────────────

    private void detectSSN(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.SSN, PIIType.SSN, 1.0, out);
    }

    private void detectCreditCard(String text, List<PIIMatch> out) {
        Matcher m = RegexPatterns.CREDIT_CARD.matcher(text);
        while (m.find()) {
            String raw    = m.group();
            String digits = TextUtils.digitsOnly(raw);
            if (digits.length() >= 13 && digits.length() <= 19
                    && TextUtils.luhnCheck(digits)) {
                out.add(match(PIIType.CREDIT_CARD, raw, m.start(), m.end(), 1.0));
            }
        }
    }

    private void detectEmail(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.EMAIL, PIIType.EMAIL, 0.97, out);
    }

    private void detectPhone(String text, List<PIIMatch> out) {
        Matcher m = RegexPatterns.PHONE.matcher(text);
        while (m.find()) {
            String raw    = m.group();
            String digits = TextUtils.digitsOnly(raw);
            // Must be exactly 10 NANP digits (or 11 if leading 1)
            if (digits.length() == 10
                    || (digits.length() == 11 && digits.charAt(0) == '1')) {
                out.add(match(PIIType.PHONE, raw, m.start(), m.end(), 0.90));
            }
        }
    }

    private void detectIpAddresses(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.IPV4, PIIType.IP_ADDRESS, 0.95, out);
        addAll(text, RegexPatterns.IPV6, PIIType.IP_ADDRESS, 0.90, out);
    }

    private void detectApiKey(String text, List<PIIMatch> out) {
        Matcher m = RegexPatterns.API_KEY.matcher(text);
        while (m.find()) {
            String raw = m.group();
            // Short generic hex segments with low entropy are likely version
            // strings or colour codes — require minimum entropy
            if (raw.length() < 20 && TextUtils.shannonEntropy(raw) < API_KEY_MIN_ENTROPY) {
                continue;
            }
            out.add(match(PIIType.API_KEY, raw, m.start(), m.end(), 0.93));
        }
    }

    private void detectPassword(String text, List<PIIMatch> out) {
        Matcher m = RegexPatterns.PASSWORD.matcher(text);
        while (m.find()) {
            // Capture the full match (keyword + value)
            out.add(match(PIIType.PASSWORD, m.group(), m.start(), m.end(), 0.88));
        }
    }

    private void detectDob(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.DATE_OF_BIRTH, PIIType.DATE_OF_BIRTH, 0.92, out);
    }

    private void detectCoordinates(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.COORDINATES, PIIType.COORDINATES, 0.88, out);
    }

    private void detectBankNumbers(String text, List<PIIMatch> out) {
        addAll(text, RegexPatterns.BANK_ROUTING, PIIType.BANK_ACCOUNT, 0.92, out);
        addAll(text, RegexPatterns.IBAN,         PIIType.BANK_ACCOUNT, 0.90, out);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void addAll(String text, Pattern p, PIIType type,
                        double confidence, List<PIIMatch> out) {
        Matcher m = p.matcher(text);
        while (m.find()) {
            out.add(match(type, m.group(), m.start(), m.end(), confidence));
        }
    }

    private PIIMatch match(PIIType type, String value,
                           int start, int end, double confidence) {
        return new PIIMatch(type, value, start, end,
                            DetectionSource.HEURISTIC, confidence);
    }

    private boolean isEnabled(PIIType type) {
        return enabledTypes.isEmpty() || enabledTypes.contains(type);
    }
}
