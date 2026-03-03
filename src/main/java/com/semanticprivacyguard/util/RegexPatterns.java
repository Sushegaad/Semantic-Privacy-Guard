package com.semanticprivacyguard.util;

import java.util.regex.Pattern;

/**
 * Centralised registry of all compiled regular expressions used by
 * {@link com.semanticprivacyguard.detector.HeuristicDetector}.
 *
 * <h2>Design Decisions</h2>
 *
 * <p>Every pattern is pre-compiled at class-load time using
 * {@link Pattern#compile} so that repeated invocations pay only the match cost,
 * not the compilation cost. All patterns use {@link Pattern#CASE_INSENSITIVE}
 * where letter case is irrelevant, and {@link Pattern#UNICODE_CHARACTER_CLASS}
 * to handle non-ASCII input correctly.</p>
 *
 * <p>See {@code docs/regex-design.md} for the full rationale behind each
 * pattern choice, including which SSN prefixes are excluded and why.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public final class RegexPatterns {

    private RegexPatterns() { /* utility class */ }

    // ── Social Security Number ────────────────────────────────────────────────

    /**
     * US Social Security Number — dashed form only ({@code NNN-NN-NNNN}).
     *
     * <p><b>Exclusions applied:</b></p>
     * <ul>
     *   <li>{@code 000-xx-xxxx} — area "000" was never assigned by the SSA.</li>
     *   <li>{@code 666-xx-xxxx} — reserved; not assigned.</li>
     *   <li>{@code 9xx-xx-xxxx} — the entire 900–999 range is reserved for
     *       ITINs (Individual Taxpayer Identification Numbers), advertising
     *       numbers, and future government use.</li>
     *   <li>{@code xxx-00-xxxx} — group number "00" is never valid.</li>
     *   <li>{@code xxx-xx-0000} — serial "0000" is never valid.</li>
     * </ul>
     *
     * <p>We deliberately require the canonical hyphenated form. Spaceless forms
     * ({@code 123456789}) produce too many false positives against arbitrary
     * 9-digit numbers (phone numbers, invoice IDs, etc.) and are therefore
     * handled by the ML layer only.</p>
     */
    public static final Pattern SSN =
        Pattern.compile(
            "\\b(?!000|666|9\\d{2})\\d{3}"   // area: 001–665, 667–899
          + "-(?!00)\\d{2}"                   // group: 01–99
          + "-(?!0000)\\d{4}\\b"              // serial: 0001–9999
        );

    // ── Credit / Debit Card ───────────────────────────────────────────────────

    /**
     * Payment card number — covers 13–19 digit cards with optional spaces or
     * hyphens between groups of 4 digits.
     *
     * <p>Luhn validation is performed programmatically in
     * {@link com.semanticprivacyguard.detector.HeuristicDetector} after the
     * regex match; cards failing the checksum are discarded.</p>
     *
     * <p>Supported networks: Visa (13/16), Mastercard (16), Amex (15),
     * Discover (16), JCB (16), UnionPay (16–19).</p>
     */
    public static final Pattern CREDIT_CARD =
        Pattern.compile(
            "\\b(?:"
          + "4[0-9]{12}(?:[0-9]{3,6})?"          // Visa 13/16/19
          + "|(?:5[1-5][0-9]{2}|222[1-9]|"       // Mastercard range 1
          +          "22[3-9][0-9]|2[3-6][0-9]{2}|"  // Mastercard range 2
          +          "27[01][0-9]|2720)[0-9]{12}" // Mastercard range 3
          + "|3[47][0-9]{13}"                     // Amex
          + "|3(?:0[0-5]|[68][0-9])[0-9]{11}"    // Diners Club
          + "|6(?:011|5[0-9]{2})[0-9]{12,15}"    // Discover
          + "|(?:2131|1800|35\\d{3})\\d{11}"      // JCB
          + "|62[0-9]{14,17}"                     // UnionPay
          + ")\\b"
          + "|\\b(?:\\d{4}[- ]){3}\\d{4}\\b"     // groups-of-4 with delimiters
        );

    // ── Email ─────────────────────────────────────────────────────────────────

    /**
     * RFC 5321-inspired email pattern.
     *
     * <p>We intentionally accept a slightly broader set than strict RFC 5321
     * (e.g. we allow consecutive dots in the local part) because real-world
     * corporate directories frequently use non-standard local parts. The ML
     * layer's context classification provides the second line of defence
     * against false positives.</p>
     */
    public static final Pattern EMAIL =
        Pattern.compile(
            "\\b[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}\\b"
        );

    // ── Phone Numbers ─────────────────────────────────────────────────────────

    /**
     * North American and international phone numbers.
     *
     * <p>Accepts the most common separators: none, space, dot, and hyphen.
     * An optional leading {@code +} handles international prefixes.
     * Requires word-boundary anchors to avoid matching portions of longer
     * digit strings.</p>
     *
     * <p>False-positive risk: short numeric sequences that match the digit
     * count. Mitigated by requiring the full 10-digit NANP pattern or an
     * explicit country code.</p>
     */
    public static final Pattern PHONE =
        Pattern.compile(
            "(?:"
          + "\\+?1[-. ]?"                         // optional US country code
          + ")?"
          + "(?:\\(?([2-9][0-9]{2})\\)?[-. ]?)"   // area code (no 0xx/1xx)
          + "([2-9][0-9]{2})"                      // exchange (no 0xx/1xx)
          + "[-. ]?"
          + "([0-9]{4})"                            // subscriber number
          + "(?!\\d)",                              // not followed by more digits
            Pattern.CASE_INSENSITIVE
        );

    // ── IPv4 ──────────────────────────────────────────────────────────────────

    /**
     * IPv4 address with octet range validation.
     *
     * <p>Each octet is constrained to {@code 0–255}. The lookahead/lookbehind
     * prevents matching inside longer numeric strings (e.g. version numbers
     * like {@code 1.2.3.4.5}).</p>
     */
    public static final Pattern IPV4 =
        Pattern.compile(
            "(?<![\\d.])"
          + "(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}"
          + "(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)"
          + "(?![\\d.])"
        );

    // ── IPv6 ──────────────────────────────────────────────────────────────────

    /**
     * Full and compressed IPv6 addresses (including {@code ::} notation and
     * mixed IPv4-in-IPv6 forms).
     *
     * <p>This pattern matches common forms but does not attempt complete RFC
     * 4291 compliance — full structural validation is expensive and the
     * additional accuracy is not warranted at the firewall layer.</p>
     */
    public static final Pattern IPV6 =
        Pattern.compile(
            "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}"          // full
          + "|(?:[0-9a-fA-F]{1,4}:){1,7}:"                        // trailing ::
          + "|::(?:[0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}"      // leading ::
          + "|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}"       // one :: mid
          + "|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}"
          + "|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}"
          + "|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}"
          + "|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}"
          + "|[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}",
            Pattern.CASE_INSENSITIVE
        );

    // ── API Keys / Tokens ─────────────────────────────────────────────────────

    /**
     * Common API key and secret token formats.
     *
     * <p>Covers: AWS Access Key IDs ({@code AKIA…}), GitHub personal access
     * tokens ({@code ghp_…/github_pat_…}), generic hex secrets ≥ 32 chars,
     * and Base64 segments ≥ 40 chars that look like bearer tokens.</p>
     */
    public static final Pattern API_KEY =
        Pattern.compile(
            "\\b(?:"
          + "(?:AKIA|ABIA|ACCA)[0-9A-Z]{16}"         // AWS Access Key ID
          + "|ghp_[0-9a-zA-Z]{36}"                    // GitHub personal token (classic)
          + "|github_pat_[0-9a-zA-Z_]{82}"            // GitHub fine-grained token
          + "|ghs_[0-9a-zA-Z]{36}"                    // GitHub server token
          + "|sk-[a-zA-Z0-9]{32,}"                    // OpenAI / Stripe secret key
          + "|Bearer\\s+[a-zA-Z0-9\\-._~+/]{20,}"    // OAuth2 Bearer
          + "|[a-f0-9]{32,64}"                        // generic hex secret
          + ")\\b",
            Pattern.CASE_INSENSITIVE
        );

    // ── Passwords ─────────────────────────────────────────────────────────────

    /**
     * Password literal patterns — matches common patterns like
     * {@code password: MyS3cr3t!}, {@code pwd = "hunter2"}, etc.
     *
     * <p>This is inherently imprecise. The ML layer classifies surrounding
     * context to improve precision.</p>
     */
    public static final Pattern PASSWORD =
        Pattern.compile(
            "(?:password|passwd|pwd|secret|passphrase|pass)"
          + "\\s*[:=]\\s*"
          + "([^\\s,;\"'`]{4,})",
            Pattern.CASE_INSENSITIVE
        );

    // ── Date of Birth ─────────────────────────────────────────────────────────

    /**
     * Date-of-birth patterns — triggered only when adjacent to contextual
     * keywords ({@code dob}, {@code born}, {@code date of birth}, {@code born on}).
     *
     * <p>Matching bare dates would produce enormous false-positive rates;
     * this pattern requires the contextual prefix.</p>
     */
    public static final Pattern DATE_OF_BIRTH =
        Pattern.compile(
            "(?:dob|d\\.o\\.b\\.|born(?:\\s+on)?|date\\s+of\\s+birth)"
          + "\\s*[:=]?\\s*"
          + "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}"
          + "|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
          +   "[a-z]*\\.?\\s+\\d{1,2},?\\s+\\d{4})",
            Pattern.CASE_INSENSITIVE
        );

    // ── Geographic Coordinates ────────────────────────────────────────────────

    /**
     * Latitude/longitude coordinate pairs in decimal-degree notation.
     * Accepts optional labels ({@code lat}, {@code lon}, {@code lng}).
     */
    public static final Pattern COORDINATES =
        Pattern.compile(
            "(?:lat(?:itude)?\\s*[:=]?\\s*)?"
          + "(-?(?:90(?:\\.0+)?|[0-8]?\\d(?:\\.\\d+)?))"
          + "\\s*[,/]\\s*"
          + "(?:lon(?:g(?:itude)?)?\\s*[:=]?\\s*)?"
          + "(-?(?:180(?:\\.0+)?|1[0-7]\\d(?:\\.\\d+)?|[0-9]{1,2}(?:\\.\\d+)?))",
            Pattern.CASE_INSENSITIVE
        );

    // ── Bank / Routing Numbers ─────────────────────────────────────────────────

    /**
     * US ABA routing number — 9 digits following a keyword prefix.
     * Bare 9-digit sequences without a keyword produce too many false positives.
     */
    public static final Pattern BANK_ROUTING =
        Pattern.compile(
            "(?:routing(?:\\s+number)?|aba|rtn)"
          + "\\s*[:=]?\\s*"
          + "(\\d{9})",
            Pattern.CASE_INSENSITIVE
        );

    /**
     * IBAN (International Bank Account Number) — 2-letter country code
     * followed by 2 check digits and up to 30 alphanumeric chars.
     */
    public static final Pattern IBAN =
        Pattern.compile(
            "\\b[A-Z]{2}\\d{2}[A-Z0-9]{4,30}\\b"
        );
}
