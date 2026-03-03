package com.semanticprivacyguard.model;

/**
 * Enumeration of all PII (Personally Identifiable Information) types that
 * Semantic Privacy Guard can detect and redact.
 *
 * <p>Each type carries a human-readable label used when generating tokens and
 * reports. Types are ordered roughly from highest (most damaging if leaked) to
 * lowest sensitivity so that callers can apply threshold-based filtering.</p>
 *
 * @author Hemant Naik
 * @since 1.0.0
 */
public enum PIIType {

    // ── Highest sensitivity ──────────────────────────────────────────────────

    /** US Social Security Number (e.g. {@code 123-45-6789}). */
    SSN("SSN", 10),

    /** Payment card number validated by the Luhn algorithm. */
    CREDIT_CARD("CREDIT_CARD", 10),

    /** API key, OAuth token, JWT, or similar bearer credential. */
    API_KEY("API_KEY", 9),

    /** Password or passphrase literal appearing in plaintext. */
    PASSWORD("PASSWORD", 9),

    // ── High sensitivity ─────────────────────────────────────────────────────

    /** Medical record number, diagnosis code, or HIPAA-protected identifier. */
    MEDICAL_RECORD("MEDICAL_RECORD", 8),

    /** Bank account or routing number. */
    BANK_ACCOUNT("BANK_ACCOUNT", 8),

    /** Passport, driver's licence, or national ID number. */
    GOVERNMENT_ID("GOVERNMENT_ID", 8),

    // ── Medium sensitivity ────────────────────────────────────────────────────

    /** Personal or corporate email address. */
    EMAIL("EMAIL", 6),

    /** Phone number in any common format. */
    PHONE("PHONE", 6),

    /** Full name detected via ML context classifier. */
    PERSON_NAME("PERSON_NAME", 6),

    /** Date of birth. */
    DATE_OF_BIRTH("DATE_OF_BIRTH", 6),

    // ── Lower sensitivity ─────────────────────────────────────────────────────

    /** Physical street address. */
    ADDRESS("ADDRESS", 4),

    /** IPv4 or IPv6 address. */
    IP_ADDRESS("IP_ADDRESS", 4),

    /** Organisation or company name detected via ML. */
    ORGANIZATION("ORGANIZATION", 3),

    /** GPS / geolocation coordinates. */
    COORDINATES("COORDINATES", 3),

    // ── Catch-all ─────────────────────────────────────────────────────────────

    /**
     * Catch-all for generic sensitive tokens that do not fit a specific type
     * but were flagged by the ML classifier with sufficient confidence.
     */
    GENERIC_PII("PII", 5);

    // ─────────────────────────────────────────────────────────────────────────

    /** Short uppercase label embedded in replacement tokens (e.g. {@code [EMAIL_1]}). */
    private final String label;

    /**
     * Severity score on a 1–10 scale; higher means the type is treated with
     * greater urgency when filtering by minimum severity threshold.
     */
    private final int severity;

    PIIType(String label, int severity) {
        this.label    = label;
        this.severity = severity;
    }

    /** Returns the short label used inside replacement tokens. */
    public String getLabel() {
        return label;
    }

    /** Returns the severity score (1–10). */
    public int getSeverity() {
        return severity;
    }
}
