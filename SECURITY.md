# Security Policy

## Supported Versions

| Version | Status          | Supported Until    |
|---------|-----------------|--------------------|
| 1.x     | ✅ Active        | Current + 12 months |
| < 1.0   | ❌ Not supported | —                  |

Only the **latest patch release** within the active major version receives
security fixes.  Always upgrade to the latest `1.x.y` before reporting a
vulnerability.

---

## Reporting a Vulnerability

> **Do not open a public GitHub Issue for security vulnerabilities.**
> Public disclosure before a fix is available puts all users at risk.

### Preferred path — GitHub Private Vulnerability Reporting

1. Go to the repository's **Security** tab.
2. Click **"Report a vulnerability"** (GitHub Private Vulnerability Reporting).
3. Fill in the form with as much detail as possible (see below).
4. You will receive an acknowledgement within **48 hours**.

### Alternative — Encrypted email

If you cannot use GitHub's private reporting, email:

```
hemant.naik@gmail.com
```

Use the subject line: `[SPG SECURITY] <brief description>`

Encrypt with GPG key `[publish your key fingerprint here]` if the content is
highly sensitive.

---

## What to include in your report

A high-quality report speeds up triage and benefits every user.  Please include:

| Field | Detail |
|-------|--------|
| **Affected version(s)** | Which release(s) are vulnerable? |
| **Affected component** | e.g. `HeuristicDetector`, `NaiveBayesClassifier` |
| **Attack surface** | How can an attacker reach the vulnerable code? |
| **Impact** | What can an attacker achieve? (PII leakage, DoS, bypass, …) |
| **Proof of concept** | Minimal code or input that reproduces the issue |
| **CWE / CVSS estimate** | If known (we will calculate a CVSS 4.0 score ourselves) |
| **Suggested fix** | Optional but appreciated |

---

## CVE Response Process

```
Report received
      │
      ▼  (within 48 h)
 Acknowledge reporter + assign internal tracking ID (SPG-SEC-YYYY-NNN)
      │
      ▼  (within 5 business days)
 Reproduce + triage: confirm severity, affected versions, scope
      │
      ├─ Low / Info ──► Fix in next regular release; credit in CHANGELOG
      │
      ├─ Medium (CVSS 4–6) ──► Fix within 30 days; patch release; CVE requested
      │
      └─ High / Critical (CVSS 7+) ──► Fix within 7 days; out-of-band patch release
                                        + GitHub Security Advisory published
                                        + CVE assigned via GitHub / Mitre
                                        + Coordinated disclosure with reporter
      │
      ▼
 Patch merged to main + release tagged
      │
      ▼
 GitHub Security Advisory published (includes CVE number, affected version,
 fix version, mitigation steps, and credit to reporter)
      │
      ▼
 Reporter notified; embargo lifted
```

### Timelines summary

| Severity | Acknowledgement | Fix target | Disclosure |
|----------|----------------|------------|------------|
| Critical (9–10) | 24 h | 7 days | After fix ships |
| High (7–8.9)    | 48 h | 7 days | After fix ships |
| Medium (4–6.9)  | 48 h | 30 days | After fix ships |
| Low (0.1–3.9)   | 5 days | Next regular release | With release notes |

---

## Scope

Vulnerabilities in scope include but are not limited to:

- **PII bypass** — crafted input that causes SPG to silently miss PII that it should detect
- **Regex ReDoS** — patterns that exhibit catastrophic backtracking on adversarial input
- **Information leakage** — log statements or exception messages that expose PII
- **Dependency vulnerabilities** — CVEs in transitive runtime dependencies (SPG is currently zero-dependency but test-scope dependencies are in scope too)
- **Cryptographic weaknesses** — if cryptographic primitives are added in future versions

Out of scope:

- False-positive rates (expected ML behaviour, not a security defect)
- Performance issues that are not denial-of-service exploitable
- Issues in example / playground code in `docs/`

---

## Security Design Principles

SPG is built with the following security invariants:

1. **Zero runtime dependencies** — the library has no third-party code in the
   production JAR, eliminating the most common source of supply-chain
   vulnerabilities (Log4Shell, etc.).

2. **Immutable configuration** — `SPGConfig` objects are immutable after
   construction; no shared mutable state.

3. **Thread safety** — all detector and tokenizer implementations are
   thread-safe by design; state is passed through method parameters, not
   instance fields.

4. **No reflection, no serialisation** — the library does not use Java
   reflection, `ObjectInputStream`, or any deserialization path, eliminating
   whole classes of deserialization attacks.

5. **Fail-closed** — if the ML classifier is not fitted, it throws rather than
   silently returning a benign classification.

---

## Hall of Fame

We gratefully acknowledge researchers who responsibly disclose vulnerabilities.

| Researcher | Vulnerability | CVE | Fixed in |
|------------|---------------|-----|----------|
| *(none yet — be the first!)* | — | — | — |

---

## Security Changelog

| Date | Version | Summary |
|------|---------|---------|
| 2026-03-01 | 1.0.0 | Initial release — no known CVEs |

---

*This policy is adapted from the [GitHub Security Policy template](https://docs.github.com/en/code-security/getting-started/adding-a-security-policy-to-your-repository)
and follows responsible disclosure best practices.*
