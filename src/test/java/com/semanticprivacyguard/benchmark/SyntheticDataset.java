package com.semanticprivacyguard.benchmark;

import com.semanticprivacyguard.model.PIIType;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates the canonical ground-truth labeled dataset used by the benchmark.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li>At least 10 positive samples per covered PII type, embedded in
 *       realistic surrounding context so the detector cannot cheat by matching
 *       bare tokens without contextual validation.</li>
 *   <li>At least 15 negative (clean) samples containing numbers, dates, and
 *       token shapes that superficially resemble PII but must NOT be flagged.</li>
 *   <li>All credit card numbers are Luhn-valid.</li>
 *   <li>All NANP phone numbers use area codes and exchanges in [2-9]XX — never
 *       0xx or 1xx — to respect the NANP validity rules baked into
 *       {@link com.semanticprivacyguard.util.RegexPatterns#PHONE}.</li>
 *   <li>PASSWORD and DATE_OF_BIRTH samples include the required keyword prefix
 *       because the heuristic patterns are keyword-anchored.</li>
 * </ul>
 *
 * <p>Extend this class to add domain-specific samples before running a
 * production benchmark.</p>
 */
public final class SyntheticDataset {

    private SyntheticDataset() { /* utility — not instantiable */ }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Generates and returns all labeled samples (positives + negatives).
     *
     * @return an unmodifiable list of {@link LabeledSample} instances
     */
    public static List<LabeledSample> generate() {
        List<LabeledSample> all = new ArrayList<>();

        all.addAll(ssnSamples());
        all.addAll(emailSamples());
        all.addAll(phoneSamples());
        all.addAll(creditCardSamples());
        all.addAll(apiKeySamples());
        all.addAll(passwordSamples());
        all.addAll(ipAddressSamples());
        all.addAll(bankAccountSamples());
        all.addAll(dateOfBirthSamples());
        all.addAll(negativeSamples());

        return List.copyOf(all);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper factories
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Creates a positive {@link LabeledSample} with a single labeled span
     * located by searching for {@code piiSubstring} inside {@code text}.
     *
     * @throws AssertionError if {@code piiSubstring} is not found in {@code text}
     */
    static LabeledSample pos(String text, PIIType type, String piiSubstring) {
        int idx = text.indexOf(piiSubstring);
        if (idx < 0) {
            throw new AssertionError(
                "piiSubstring '" + piiSubstring + "' not found in text: " + text);
        }
        return new LabeledSample(text,
                List.of(new LabeledSpan(type, idx, idx + piiSubstring.length())));
    }

    /**
     * Creates a negative {@link LabeledSample} — clean text with no labeled PII.
     */
    static LabeledSample neg(String text) {
        return new LabeledSample(text, List.of());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — SSN
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> ssnSamples() {
        // Area codes: 001-665 and 667-899; group: 01-99; serial: 0001-9999
        return List.of(
            pos("Employee on file — SSN: 234-56-7890, start date 2023-01-10.", PIIType.SSN, "234-56-7890"),
            pos("Applicant social security number is 321-54-9876.", PIIType.SSN, "321-54-9876"),
            pos("Tax ID (SSN) used for filing: 456-78-1234.", PIIType.SSN, "456-78-1234"),
            pos("Patient demographics include SSN 523-45-6789 recorded at intake.", PIIType.SSN, "523-45-6789"),
            pos("Background check requires SSN 214-67-3890 to proceed.", PIIType.SSN, "214-67-3890"),
            pos("Payroll record: name=Jane Doe, SSN=345-67-8901, dept=Engineering.", PIIType.SSN, "345-67-8901"),
            pos("SSN for W-2: 412-33-5678 — please verify spelling of last name.", PIIType.SSN, "412-33-5678"),
            pos("Benefits enrollment requires a verified SSN: 567-89-2345.", PIIType.SSN, "567-89-2345"),
            pos("The claimant's SSN is 678-90-3456 as listed on the application.", PIIType.SSN, "678-90-3456"),
            pos("Direct deposit set up for SSN 789-01-2345 effective next cycle.", PIIType.SSN, "789-01-2345"),
            pos("Retirement account linked to SSN 234-11-9876 updated successfully.", PIIType.SSN, "234-11-9876"),
            pos("Credit check initiated for SSN 312-44-5670 — consent obtained.", PIIType.SSN, "312-44-5670")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — EMAIL
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> emailSamples() {
        return List.of(
            pos("Please reach out to alice.doe@corp.com for further details.", PIIType.EMAIL, "alice.doe@corp.com"),
            pos("Send the invoice to billing@acme.org as soon as possible.", PIIType.EMAIL, "billing@acme.org"),
            pos("The primary contact is support@example.org — available 24/7.", PIIType.EMAIL, "support@example.org"),
            pos("CC john.smith+lists@gmail.com on all future correspondence.", PIIType.EMAIL, "john.smith+lists@gmail.com"),
            pos("Account registered under noreply@notifications.io.", PIIType.EMAIL, "noreply@notifications.io"),
            pos("Forward the report to data-team@analytics.co.", PIIType.EMAIL, "data-team@analytics.co"),
            pos("Emergency contact email: dr.jones@hospital.nhs.uk.", PIIType.EMAIL, "dr.jones@hospital.nhs.uk"),
            pos("HR system account: hr.admin@internal.company.net.", PIIType.EMAIL, "hr.admin@internal.company.net"),
            pos("Vendor POC is procurement+vendor@bigco.io — copy finance.", PIIType.EMAIL, "procurement+vendor@bigco.io"),
            pos("System alerts route to ops-alerts@monitoring.dev.", PIIType.EMAIL, "ops-alerts@monitoring.dev"),
            pos("The user's registered email is jane_doe@university.edu.", PIIType.EMAIL, "jane_doe@university.edu"),
            pos("Bounce notification for user@sub.domain.example.com recorded.", PIIType.EMAIL, "user@sub.domain.example.com")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — PHONE
    // Area codes and exchanges must start with [2-9] (NANP rule).
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> phoneSamples() {
        return List.of(
            // (800) 555-2368 — area 800, exchange 555 (5xx, valid)
            pos("Customer care hotline: (800) 555-2368, open weekdays 9-5.", PIIType.PHONE, "(800) 555-2368"),
            // (212) 555-4567
            pos("Call the New York office at (212) 555-4567 for appointments.", PIIType.PHONE, "(212) 555-4567"),
            // (415) 867-5309
            pos("Our San Francisco number is (415) 867-5309.", PIIType.PHONE, "(415) 867-5309"),
            // (312) 345-6789
            pos("Chicago branch phone: (312) 345-6789 — ask for sales.", PIIType.PHONE, "(312) 345-6789"),
            // (713) 234-5678
            pos("Houston support line is (713) 234-5678, press 2 for technical.", PIIType.PHONE, "(713) 234-5678"),
            // (617) 890-1234
            pos("Patient callback number: (617) 890-1234, extension 42.", PIIType.PHONE, "(617) 890-1234"),
            // (404) 567-8901
            pos("Atlanta dispatch reached at (404) 567-8901 around the clock.", PIIType.PHONE, "(404) 567-8901"),
            // (206) 345-9012
            pos("Seattle office: (206) 345-9012 — after hours press 0.", PIIType.PHONE, "(206) 345-9012"),
            // (305) 678-2345
            pos("Miami showroom: (305) 678-2345, ask for the manager.", PIIType.PHONE, "(305) 678-2345"),
            // (602) 789-3456
            pos("Phoenix fulfillment centre phone is (602) 789-3456.", PIIType.PHONE, "(602) 789-3456"),
            // (503) 234-6789
            pos("Portland helpdesk: (503) 234-6789, available Mon–Fri.", PIIType.PHONE, "(503) 234-6789"),
            // (702) 345-7890
            pos("Las Vegas reservation line: (702) 345-7890.", PIIType.PHONE, "(702) 345-7890")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — CREDIT_CARD (all Luhn-valid)
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> creditCardSamples() {
        return List.of(
            // 4532015112830366 — Visa, Luhn valid
            pos("Card on file: 4532015112830366 (Visa).", PIIType.CREDIT_CARD, "4532015112830366"),
            // 4539578763621486 — Visa, Luhn valid
            pos("Charge to card 4539578763621486, billing zip 10001.", PIIType.CREDIT_CARD, "4539578763621486"),
            // 5425233430109903 — Mastercard, Luhn valid
            pos("Mastercard ending in 9903: 5425233430109903.", PIIType.CREDIT_CARD, "5425233430109903"),
            // 4539 5787 6362 1486 — spaced form of the Visa above
            pos("Spaces-formatted card: 4539 5787 6362 1486.", PIIType.CREDIT_CARD, "4539 5787 6362 1486"),
            // 4111111111111111 — canonical Visa test number, Luhn valid
            pos("Test card used in staging: 4111111111111111.", PIIType.CREDIT_CARD, "4111111111111111"),
            // 4012888888881881 — Visa, Luhn valid
            pos("Dispute filed for card 4012888888881881 on 2024-03-12.", PIIType.CREDIT_CARD, "4012888888881881"),
            // 5500005555555559 — Mastercard, Luhn valid
            pos("Recurring charge on 5500005555555559 approved.", PIIType.CREDIT_CARD, "5500005555555559"),
            // 5105105105105100 — Mastercard, Luhn valid
            pos("Card 5105105105105100 flagged for unusual activity.", PIIType.CREDIT_CARD, "5105105105105100"),
            // 371449635398431 — Amex (15 digit), Luhn valid
            pos("Amex card number 371449635398431 authorised for $1,200.", PIIType.CREDIT_CARD, "371449635398431"),
            // 6011111111111117 — Discover, Luhn valid
            pos("Discover card 6011111111111117 used for subscription.", PIIType.CREDIT_CARD, "6011111111111117"),
            // 4532 0151 1283 0366 — hyphen-free spaced grouping
            pos("Receipt for card 4532 0151 1283 0366 emailed to customer.", PIIType.CREDIT_CARD, "4532 0151 1283 0366"),
            // 4916338506082832 — Visa, Luhn valid
            pos("Billing card: 4916338506082832 charged successfully.", PIIType.CREDIT_CARD, "4916338506082832")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — API_KEY
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> apiKeySamples() {
        return List.of(
            pos("AWS key in config: AKIAIOSFODNN7EXAMPLE — rotate immediately.", PIIType.API_KEY, "AKIAIOSFODNN7EXAMPLE"),
            pos("OpenAI secret: sk-abcdefghijklmnopqrstuvwxyz123456 found in log.", PIIType.API_KEY, "sk-abcdefghijklmnopqrstuvwxyz123456"),
            pos("GitHub token detected: ghp_abcdefghijklmnopqrstuvwxyz123456ab.", PIIType.API_KEY, "ghp_abcdefghijklmnopqrstuvwxyz123456ab"),
            pos("Stripe key in plaintext: sk-live_abcdefghijklmnopqrstuvwxyz12.", PIIType.API_KEY, "sk-live_abcdefghijklmnopqrstuvwxyz12"),
            pos("Second AWS credential: AKIAJ2EXAMPLE3456789 in CI pipeline.", PIIType.API_KEY, "AKIAJ2EXAMPLE3456789"),
            pos("Hex API secret 3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e found in env.", PIIType.API_KEY, "3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e"),
            pos("Bearer token in header: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI.", PIIType.API_KEY, "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI"),
            pos("GitHub server token: ghs_abcdefghijklmnopqrstuvwxyz123456ab.", PIIType.API_KEY, "ghs_abcdefghijklmnopqrstuvwxyz123456ab"),
            pos("Another AWS key: AKIAB3CDEFGHIJKLM1NO in deployment script.", PIIType.API_KEY, "AKIAB3CDEFGHIJKLM1NO"),
            pos("Generic hex token: a1b2c3d4e5f67890a1b2c3d4e5f67890 in source.", PIIType.API_KEY, "a1b2c3d4e5f67890a1b2c3d4e5f67890"),
            pos("OpenAI org key: sk-org-abcdefghijklmnopqrstuvwxyz12345 in vault.", PIIType.API_KEY, "sk-org-abcdefghijklmnopqrstuvwxyz12345"),
            pos("Long hex credential: 0a1b2c3d4e5f6789abcdef01234567890a1b2c3d found in git blame.", PIIType.API_KEY, "0a1b2c3d4e5f6789abcdef01234567890a1b2c3d")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — PASSWORD
    // Must include the keyword prefix because the pattern is keyword-anchored.
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> passwordSamples() {
        return List.of(
            pos("Config entry: password=MyS3cr3t! — change before prod deploy.", PIIType.PASSWORD, "password=MyS3cr3t!"),
            pos("Credentials file has pwd=hunter2Secure set by the ops team.", PIIType.PASSWORD, "pwd=hunter2Secure"),
            pos("Database connection string: secret=T0pS3cr3t# — never commit.", PIIType.PASSWORD, "secret=T0pS3cr3t#"),
            pos("Legacy system uses password=S3cr3t do not expose this value.", PIIType.PASSWORD, "password=S3cr3t"),
            pos("CI variable password=P@ssw0rd!2024 injected at runtime.", PIIType.PASSWORD, "password=P@ssw0rd!2024"),
            pos("Admin portal: passwd=Adm1nR0cks stored in plaintext (bad!).", PIIType.PASSWORD, "passwd=Adm1nR0cks"),
            pos("Application yml contains pass=Qwerty!9876 under database section.", PIIType.PASSWORD, "pass=Qwerty!9876"),
            pos("Deployment secret: passphrase=Corr3ctH0rs3Batt3ryStaple!", PIIType.PASSWORD, "passphrase=Corr3ctH0rs3Batt3ryStaple!"),
            pos("Service account password=Xk9#mNp2$vL7 rotated quarterly.", PIIType.PASSWORD, "password=Xk9#mNp2$vL7"),
            pos("Redis AUTH token: secret=Str0ngP@ssw0rdHere set in sentinel config.", PIIType.PASSWORD, "secret=Str0ngP@ssw0rdHere"),
            pos("Vault path exports pwd=VaultS3cr3t123 to subprocess environment.", PIIType.PASSWORD, "pwd=VaultS3cr3t123"),
            pos("SMTP relay config: password=Sm@rtM@ilP@ss99 in mail.properties.", PIIType.PASSWORD, "password=Sm@rtM@ilP@ss99")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — IP_ADDRESS
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> ipAddressSamples() {
        return List.of(
            pos("Database primary runs on 192.168.1.100 behind the load balancer.", PIIType.IP_ADDRESS, "192.168.1.100"),
            pos("Default gateway is 10.0.0.1 — do not block in firewall rules.", PIIType.IP_ADDRESS, "10.0.0.1"),
            pos("Private subnet router: 172.16.254.1 handles inter-VLAN routing.", PIIType.IP_ADDRESS, "172.16.254.1"),
            pos("Attacker IP logged: 203.0.113.42 — blocked at edge.", PIIType.IP_ADDRESS, "203.0.113.42"),
            pos("NTP server configured as 216.239.35.0 in timesyncd.conf.", PIIType.IP_ADDRESS, "216.239.35.0"),
            pos("VPN endpoint: 198.51.100.17 — accessible on port 1194.", PIIType.IP_ADDRESS, "198.51.100.17"),
            pos("Secondary DNS resolver: 8.8.4.4 (Google) used as fallback.", PIIType.IP_ADDRESS, "8.8.4.4"),
            pos("CI runner agent registered at 10.10.10.50 in the build network.", PIIType.IP_ADDRESS, "10.10.10.50"),
            pos("Production web server: 192.0.2.55 — certificate renewed.", PIIType.IP_ADDRESS, "192.0.2.55"),
            pos("Monitoring agent polls 172.31.0.1 every 60 seconds.", PIIType.IP_ADDRESS, "172.31.0.1"),
            pos("Kubernetes node IP: 10.244.0.5 assigned by flannel CNI.", PIIType.IP_ADDRESS, "10.244.0.5"),
            pos("Remote desktop allowed from 203.0.113.99 only — per policy.", PIIType.IP_ADDRESS, "203.0.113.99")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — BANK_ACCOUNT (IBAN)
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> bankAccountSamples() {
        return List.of(
            pos("Wire to GB29NWBK60161331926819 — sort code and account embedded.", PIIType.BANK_ACCOUNT, "GB29NWBK60161331926819"),
            pos("EU transfer destination: DE89370400440532013000.", PIIType.BANK_ACCOUNT, "DE89370400440532013000"),
            pos("Vendor payment via FR7630006000011234567890189.", PIIType.BANK_ACCOUNT, "FR7630006000011234567890189"),
            pos("Dutch IBAN for refund: NL91ABNA0417164300 confirmed.", PIIType.BANK_ACCOUNT, "NL91ABNA0417164300"),
            pos("Swiss bank account IBAN: CH9300762011623852957.", PIIType.BANK_ACCOUNT, "CH9300762011623852957"),
            pos("Invoice payable to IT60X0542811101000000123456.", PIIType.BANK_ACCOUNT, "IT60X0542811101000000123456"),
            pos("Spanish IBAN for payroll: ES9121000418450200051332.", PIIType.BANK_ACCOUNT, "ES9121000418450200051332"),
            pos("Belgian account: BE68539007547034 — BIC GEBABEBB.", PIIType.BANK_ACCOUNT, "BE68539007547034"),
            pos("Polish IBAN PL61109010140000071219812874 for international wire.", PIIType.BANK_ACCOUNT, "PL61109010140000071219812874"),
            pos("Austrian payment account AT611904300234573201.", PIIType.BANK_ACCOUNT, "AT611904300234573201"),
            pos("Swedish IBAN SE4550000000058398257466 on supplier invoice.", PIIType.BANK_ACCOUNT, "SE4550000000058398257466"),
            pos("Norwegian account NO9386011117947 listed in tax records.", PIIType.BANK_ACCOUNT, "NO9386011117947")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Positive samples — DATE_OF_BIRTH
    // Must include keyword prefix because the pattern is keyword-anchored.
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> dateOfBirthSamples() {
        return List.of(
            pos("Patient intake form — dob: 03/15/1985, blood type O+.", PIIType.DATE_OF_BIRTH, "dob: 03/15/1985"),
            pos("HR record: born: 12/25/1990, department Engineering.", PIIType.DATE_OF_BIRTH, "born: 12/25/1990"),
            pos("Claim form section 2: date of birth: 07/04/1976.", PIIType.DATE_OF_BIRTH, "date of birth: 07/04/1976"),
            pos("Insurance beneficiary dob: 11/11/1988 — policy #AB1234.", PIIType.DATE_OF_BIRTH, "dob: 11/11/1988"),
            pos("School enrolment — date of birth: 05/22/2005, grade 9.", PIIType.DATE_OF_BIRTH, "date of birth: 05/22/2005"),
            pos("Voter registration: born on 08/30/1972 — district 5.", PIIType.DATE_OF_BIRTH, "born on 08/30/1972"),
            pos("Passport application: d.o.b.: 02/14/1995, expiry 2035.", PIIType.DATE_OF_BIRTH, "d.o.b.: 02/14/1995"),
            pos("Credit application dob: 09/09/1980 confirmed by ID check.", PIIType.DATE_OF_BIRTH, "dob: 09/09/1980"),
            pos("Medical file — born: 01/01/2000, paediatric ward.", PIIType.DATE_OF_BIRTH, "born: 01/01/2000"),
            pos("Background check: date of birth: 06/18/1965, reference #7890.", PIIType.DATE_OF_BIRTH, "date of birth: 06/18/1965"),
            pos("Gym membership: dob: 04/30/1999 stored per GDPR article 9.", PIIType.DATE_OF_BIRTH, "dob: 04/30/1999"),
            pos("Travel visa: born on 10/10/1983, nationality British.", PIIType.DATE_OF_BIRTH, "born on 10/10/1983")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Negative samples — clean text that should NOT be flagged
    // ─────────────────────────────────────────────────────────────────────────

    private static List<LabeledSample> negativeSamples() {
        return List.of(
            // Generic prose
            neg("I ate an apple and watched an Apple keynote on YouTube."),
            neg("The quick brown fox jumps over the lazy dog."),
            neg("In the book, John meets Mary at the marketplace in chapter 5."),
            neg("The committee approved the budget for Q3 with a 4-to-1 vote."),
            neg("Version 2024.01.15 of the library was released to Maven Central."),

            // Numbers that look like PII but aren't
            neg("The server returned HTTP error code 404 after 3 retries."),
            neg("Pi is approximately 3.14159265358979, a famous mathematical constant."),
            neg("Product SKU is 100-22-9999 from the warehouse catalogue."),
            neg("Build artifact version: 1.0.0-SNAPSHOT released on 2023-11-30."),
            neg("Team roster has 12 members; the score was 110 to 98 in overtime."),

            // Dates without DOB keyword
            neg("The fiscal year ends on 12/31/2024 — submit expenses by then."),
            neg("Meeting scheduled for 03/15/2025 at 14:00 in room B201."),

            // IPs that look like versions or coordinates
            neg("Software versioned as 1.2.3.4 is not yet deployed to production."),
            neg("GPS reading shows latitude 51.5, longitude -0.12 (London centre)."),

            // Hex strings that are too short to be secrets
            neg("Checksum for the tarball is md5: a1b2c3d4 — verify before install."),
            neg("HTML colour code #58a6ff is the accent blue used in our brand guide."),

            // IBAN-like patterns that don't start with valid country codes
            neg("Reference number XZ9912345678901234 attached to the order."),

            // Clean financial text without actual PII
            neg("The company reported revenue of $4.2 billion in fiscal year 2023."),
            neg("Interest rate was set at 5.25% by the central bank last Thursday.")
        );
    }
}
