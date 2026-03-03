# 🛡 Semantic Privacy Guard

[![CI](https://github.com/hemant-naik/semantic-privacy-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/hemant-naik/semantic-privacy-guard/actions/workflows/ci.yml)
[![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-brightgreen)](https://github.com/hemant-naik/semantic-privacy-guard/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![Zero deps](https://img.shields.io/badge/runtime%20deps-0-brightgreen)](#)
[![Maven Central](https://img.shields.io/maven-central/v/com.semanticprivacyguard/semantic-privacy-guard?color=blue)](https://central.sonatype.com/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Security Policy](https://img.shields.io/badge/security-policy-orange)](SECURITY.md)

> **A lightweight, zero-dependency Java middleware that intercepts LLM prompts,
> identifies PII using a hybrid Regex + Naive Bayes approach, and redacts it
> before it leaves the corporate network.**

---

## 🚀 Live Playground

**[Try it in your browser →](https://hemant-naik.github.io/semantic-privacy-guard/)**

Paste any text, choose a redaction mode, and see instant results — 100% client-side, nothing sent to any server.

---

## Why Semantic Privacy Guard?

| Problem | SPG Solution |
|---|---|
| Employees paste customer data into ChatGPT | Intercept prompts at the API gateway layer |
| Cloud PII APIs cost $0.001/call at scale | SPG costs $0/call, runs fully offline |
| LLMs need context; full redaction breaks prompts | Structured tokens like `[EMAIL_1]` preserve sentence structure |
| 2026 EU AI Act: "Privacy by Design" required | SPG is the compliance middleware |
| OpenNLP / spaCy are heavy (50–500 MB models) | SPG is **< 250 KB** total heap footprint |

### The Disambiguation Advantage

A naive regex fires on every title-cased word. SPG's Naive Bayes context
classifier distinguishes:

```
"I ate an apple yesterday."          →  No match  (fruit)
"Contact Apple at (800) 555-1234."   →  [ORGANIZATION_1]  (company PII)
"The Gospel of John has 21 chapters" →  No match  (literary reference)
"Dear John, your SSN is 123-45-6789" →  [PERSON_NAME_1] + [SSN_1]
```

---

## Quick Start

### Maven

```xml
<dependency>
  <groupId>com.semanticprivacyguard</groupId>
  <artifactId>semantic-privacy-guard</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.semanticprivacyguard:semantic-privacy-guard:1.0.0'
```

### One-liner usage

```java
import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.model.RedactionResult;

SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

RedactionResult result = spg.redact(
    "Email Alice at alice.doe@acme.com or call (555) 867-5309. SSN: 123-45-6789."
);

System.out.println(result.getRedactedText());
// -> "Email [PERSON_NAME_1] at [EMAIL_1] or call [PHONE_1]. SSN: [SSN_1]."

System.out.println(result.getMatchCount());       // -> 4
System.out.println(result.getProcessingTimeMs()); // -> < 1 ms
```

---

## PII Types Detected

| Type | Example | Method | Severity |
|---|---|---|---|
| `SSN` | `123-45-6789` | Regex + exclusion rules | 10 |
| `CREDIT_CARD` | `4532 0151 1283 0366` | Regex + Luhn | 10 |
| `API_KEY` | `AKIAIOSFODNN7EXAMPLE` | Regex + entropy | 9 |
| `PASSWORD` | `password=MyS3cr3t` | Regex (keyword-prefixed) | 9 |
| `MEDICAL_RECORD` | `MRN123456` | ML | 8 |
| `BANK_ACCOUNT` | `GB29NWBK60161331926819` | Regex (IBAN) | 8 |
| `EMAIL` | `alice@example.com` | Regex | 6 |
| `PHONE` | `(555) 867-5309` | Regex (NANP validated) | 6 |
| `PERSON_NAME` | `Alice Johnson` | Naive Bayes ML | 6 |
| `DATE_OF_BIRTH` | `dob: 03/15/1985` | Regex (context-prefixed) | 6 |
| `IP_ADDRESS` | `192.168.1.100` | Regex (range-validated) | 4 |
| `ORGANIZATION` | `Apple` (contact context) | Naive Bayes ML | 3 |
| `COORDINATES` | `51.5074, -0.1278` | Regex (bounds-checked) | 3 |

---

## API Reference

### `SemanticPrivacyGuard.create()`

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();        // defaults
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);  // custom
```

### `redact(String text)` → `RedactionResult`

Full detection + replacement pass. Returns:

- `getRedactedText()` — sanitised string
- `getMatches()` — list of `PIIMatch` objects with type, value, span, confidence
- `getReverseMap()` — `Map<String, String>` token to original value
- `getMatchCount()` — number of detections
- `getProcessingTimeMs()` — wall-clock time

### `containsPII(String text)` → `boolean`

Fast pre-flight check (~30% faster than `redact()`) for yes/no answers.

### `analyse(String text)` → `List<PIIMatch>`

Detection without redaction — for audit and reporting pipelines.

### Configuration

```java
SPGConfig config = SPGConfig.builder()
    .redactionMode(RedactionMode.TOKEN)   // TOKEN | MASK | BLANK
    .mlConfidenceThreshold(0.70)          // 0.0–1.0; default 0.65
    .enabledTypes(Set.of(PIIType.EMAIL,   // null/empty = all types
                         PIIType.SSN))
    .minimumSeverity(6)                   // 1–10; filter low-impact types
    .buildReverseMap(true)                // disable for slight perf gain
    .heuristicEnabled(true)
    .mlEnabled(true)
    .build();
```

### Redaction Modes

| Mode | Example output | Use case |
|---|---|---|
| `TOKEN` | `[EMAIL_1]` | LLM pipelines (structure preserved) |
| `MASK` | `█████████████████` | Logs, audit trails |
| `BLANK` | `[REDACTED]` | Human-readable reports |

---

## Virtual Threads (Project Loom)

SPG is stateless and thread-safe by design. On Java 21+:

```java
// Handle 10,000 concurrent LLM prompts with zero contention
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    for (String prompt : promptBatch) {
        exec.submit(() -> {
            RedactionResult r = spg.redact(prompt);
            forwardToLLM(r.getRedactedText());
        });
    }
}
```

---

## Performance

| Approach | Throughput | False Positives |
|---|---|---|
| Naive regex (2 patterns) | 580,000 sentences/s | 60% of clean sentences |
| SPG Heuristic-only | 390,000 sentences/s | 20% |
| **SPG Full (H+ML)** | **206,000 sentences/s** | **0%** |

See [docs/benchmarks.md](docs/benchmarks.md) for full methodology and the
comparison table against cloud alternatives (AWS Comprehend, Google DLP, Presidio).

---

## Architecture

```
Input text
    |
    v
+-------------------------------------------------+
|  Layer 1: HeuristicDetector                     |
|  Regex patterns + Luhn checksum + entropy filter|
|  SSN, Email, Phone, CC, IPs, API Keys, Passwords|
+-----------------------+-------------------------+
                        |
    v
+-------------------------------------------------+
|  Layer 2: MLDetector                            |
|  Pure-Java Naive Bayes + FeatureExtractor       |
|  Person names, Organisations (context-aware)    |
+-----------------------+-------------------------+
                        |
    v
+-------------------------------------------------+
|  CompositeDetector                              |
|  De-duplicate, resolve overlaps, HYBRID merging |
+-----------------------+-------------------------+
                        |
    v
+-------------------------------------------------+
|  PIITokenizer                                   |
|  TOKEN / MASK / BLANK + reverse map             |
+-------------------------------------------------+
                        |
    v
    RedactionResult
```

---

## Building from Source

```bash
git clone https://github.com/hemant-naik/semantic-privacy-guard.git
cd semantic-privacy-guard

# Compile + test + coverage (must be >= 80%)
mvn verify

# Run benchmarks
mvn test -P benchmark

# Build JAR
mvn package -DskipTests
```

Requirements: JDK 17+ and Maven 3.8+.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All contributions welcome — especially:

- Additional training examples for the ML corpus
- New PII type patterns (medical codes, national IDs)
- Performance improvements
- Real-world benchmark data

---

## Security

See [SECURITY.md](SECURITY.md) for the CVE response process and responsible
disclosure policy.

SPG has zero runtime dependencies, eliminating supply-chain attack vectors.
All regex patterns are validated against catastrophic backtracking (ReDoS).

---

## Docs

- [Regex Design Decisions](docs/regex-design.md) — why SSN excludes `9xx`, Luhn validation, entropy filtering
- [Benchmarks](docs/benchmarks.md) — throughput and accuracy vs alternatives
- [Security Policy](SECURITY.md) — CVE process, disclosure timeline, scope

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Copyright 2026 Hemant Naik
