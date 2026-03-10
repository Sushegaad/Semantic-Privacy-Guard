# 🛡 Semantic Privacy Guard

[![CI](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml/badge.svg)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sushegaad/semantic-privacy-guard?color=blue&logo=apache-maven)](https://central.sonatype.com/artifact/io.github.sushegaad/semantic-privacy-guard)
[![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-brightgreen)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Security Policy](https://img.shields.io/badge/security-policy-orange)](SECURITY.md)
[![Live Playground](https://img.shields.io/badge/playground-live-brightgreen)](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)

> **A Java middleware that intercepts text, identifies PII using a three-layer hybrid pipeline
> (Regex + Naive Bayes ML + Apache OpenNLP NER), and redacts it before it reaches
> an LLM or leaves the corporate network — with stream-based processing for
> memory-efficient handling of large files and log streams.**

---

## 🚀 Live Playground

**[Try it in your browser →](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)**

Paste any text, choose a redaction mode, and see instant results — 100% client-side, nothing sent to any server.

---

## Why Semantic Privacy Guard?

| Problem | How SPG helps |
|---|---|
| Employees paste customer data into ChatGPT | Intercept prompts at the API gateway layer |
| Cloud PII APIs cost $0.001/call at scale | SPG costs $0/call, runs fully offline |
| LLMs need context; full redaction breaks prompts | Structured tokens like `[EMAIL_1]` preserve sentence structure |
| 2026 EU AI Act: "Privacy by Design" required | SPG is the compliance middleware |
| 50 MB log file = 150–200 MB heap per request | Stream API processes one line at a time — constant memory |
| Naive regex fires on every title-cased word | Three-layer pipeline: regex + Naive Bayes + OpenNLP NER |

### The Disambiguation Advantage

```
"I ate an apple yesterday."          →  No match   (fruit, not a name)
"Contact Apple at (800) 275-2273."   →  [ORG_1]    (company + phone)
"The Gospel of John has 21 chapters" →  No match   (literary reference)
"Dear John, your SSN is 123-45-6789" →  [PERSON_NAME_1] + [SSN_1]
"John Michael Smith confirmed."      →  [PERSON_NAME_1] (OpenNLP NER)
```

---

## Playground Screenshot

[![Semantic Privacy Guard Playground](docs/playground-screenshot.png)](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)

*The live playground detecting an SSN and an email address in real time — redacted output, detection table with confidence bars, and reverse map all visible.*

---

## Quick Start

### Maven

```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard</artifactId>
  <version>1.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.sushegaad:semantic-privacy-guard:1.1.0'
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
// → "Email [PERSON_NAME_1] at [EMAIL_1] or call [PHONE_1]. SSN: [SSN_1]."

System.out.println(result.getMatchCount());       // → 4
System.out.println(result.getProcessingTimeMs()); // → < 1 ms
```

---

## Stream-Based Processing

Loading a 50 MB log file into a `String` costs ~50 MB on the heap, and with ML tokenizer working strings you reach 150–200 MB _per concurrent request_. On a Lambda with 512 MB RAM and 10 concurrent calls that is an OOM event waiting to happen.

The `StreamProcessor` processes one line at a time. Each line is detected, redacted, written to the output, and immediately eligible for GC. Heap stays bounded by the longest single line — typically under 4 KB.

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

// File-to-file: constant heap regardless of file size
StreamRedactionSummary summary =
    spg.redactPath(Path.of("access.log"), Path.of("access.clean.log"));

System.out.println(summary);
// → StreamRedactionSummary[lines=84231, linesWithPII=312, matches=389, timeMs=740]

// InputStream / OutputStream (e.g. in a servlet filter)
try (InputStream  in  = request.getInputStream();
     OutputStream out = response.getOutputStream()) {
    spg.redactStream(in, out);
}

// Reader / Writer
spg.redactStream(request.getReader(), response.getWriter());

// Lazy Java Stream — integrates with Files.lines()
try (Stream<String> lines = Files.lines(inputPath)) {
    spg.streamProcessor()
       .redactLines(lines)
       .forEach(outputWriter::println);
}
```

Token counters are **document-scoped**: `[EMAIL_1]` on line 3 and `[EMAIL_2]` on line 7 — never two `[EMAIL_1]` tokens in the same document.

---

## NLP Integration (Apache OpenNLP)

The third detection layer uses Apache OpenNLP Named Entity Recognition — a Maximum Entropy model trained on large NLP corpora. It excels at cases the Naive Bayes layer struggles with: multi-token person names, compound organisation names, and names appearing in varied syntactic positions.

### Enable NLP

```java
// Models loaded from classpath (src/main/resources/models/)
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .build();

// Models loaded from the filesystem
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .nlpModelsDirectory(Path.of("/opt/nlp-models"))
    .nlpConfidenceThreshold(0.75)   // default 0.70
    .build();

SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);
```

### NLP Setup — Model Download

OpenNLP models are large binary files not bundled in the jar. Download them from the [Apache OpenNLP model repository](https://opennlp.sourceforge.net/models-1.5/):

```
en-ner-person.bin        (required, ~14 MB)  — person name NER
en-ner-organization.bin  (recommended, ~16 MB) — organisation name NER
en-token.bin             (recommended, ~1 MB)  — MaxEnt tokenizer
```

Place them on the classpath:

```
src/main/resources/
  models/
    en-ner-person.bin
    en-ner-organization.bin
    en-token.bin
```

Or point to a filesystem directory:

```java
.nlpModelsDirectory(Path.of("/opt/nlp-models"))
```

Add the OpenNLP runtime dependency (marked `optional` in SPG — you must add it yourself):

```xml
<dependency>
  <groupId>org.apache.opennlp</groupId>
  <artifactId>opennlp-tools</artifactId>
  <version>2.3.3</version>
</dependency>
```

### NLP Detection Types

| Detected by OpenNLP | PIIType | Notes |
|---|---|---|
| Person names | `PERSON_NAME` | Multi-token names, varied positions |
| Organisation names | `ORGANIZATION` | Compound names, acronyms |

NLP results flow through the same `CompositeDetector` de-duplication as heuristic and ML results. When two layers agree on the same span the match is promoted to `DetectionSource.HYBRID` with the higher confidence score.

### Thread Safety with Virtual Threads

`NameFinderME` is not thread-safe. `NLPDetector` uses `ThreadLocal` to give each thread its own `NameFinderME` wrapper, all sharing the same immutable `TokenNameFinderModel`. Adaptive state is cleared after every `detect()` call so no state leaks between requests. The class is safe under Java 17+ virtual threads (Project Loom).

---

## PII Types Detected

| Type | Example | Detection method | Severity |
|---|---|---|---|
| `SSN` | `123-45-6789` | Regex + exclusion rules | 10 |
| `CREDIT_CARD` | `4532 0151 1283 0366` | Regex + Luhn checksum | 10 |
| `API_KEY` | `AKIAIOSFODNN7EXAMPLE` | Regex + entropy filter | 9 |
| `PASSWORD` | `password=MyS3cr3t` | Regex (keyword-prefixed) | 9 |
| `MEDICAL_RECORD` | `MRN123456` | Naive Bayes ML | 8 |
| `BANK_ACCOUNT` | `GB29NWBK60161331926819` | Regex (IBAN) | 8 |
| `EMAIL` | `alice@example.com` | Regex | 6 |
| `PHONE` | `(555) 867-5309` | Regex (NANP validated) | 6 |
| `PERSON_NAME` | `Alice Johnson` | Naive Bayes ML + OpenNLP NER | 6 |
| `DATE_OF_BIRTH` | `dob: 03/15/1985` | Regex (context-prefixed) | 6 |
| `IP_ADDRESS` | `192.168.1.100` | Regex (range-validated) | 4 |
| `ORGANIZATION` | `Barclays Bank PLC` | Naive Bayes ML + OpenNLP NER | 3 |
| `COORDINATES` | `51.5074, -0.1278` | Regex (bounds-checked) | 3 |

---

## API Reference

### `SemanticPrivacyGuard.create()`

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();        // defaults
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);  // custom
```

### `redact(String text)` → `RedactionResult`

Full detection + replacement pass. Returns `getRedactedText()`, `getMatches()`, `getReverseMap()` (token → original, for post-LLM de-tokenisation), `getMatchCount()`, and `getProcessingTimeMs()`.

### `containsPII(String text)` → `boolean`

Fast pre-flight check (~30% faster than `redact()`) for yes/no answers.

### `analyse(String text)` → `List<PIIMatch>`

Detection without redaction — for audit and reporting pipelines.

### Stream methods

```java
// InputStream / OutputStream (UTF-8)
StreamRedactionSummary redactStream(InputStream in, OutputStream out)

// Reader / Writer
StreamRedactionSummary redactStream(Reader reader, Writer writer)

// File-to-file
StreamRedactionSummary redactPath(Path inputFile, Path outputFile)

// Access the full StreamProcessor for redactLines(Stream<String>)
StreamProcessor streamProcessor()
```

### Configuration

```java
SPGConfig config = SPGConfig.builder()
    .redactionMode(RedactionMode.TOKEN)    // TOKEN | MASK | BLANK
    .mlConfidenceThreshold(0.70)           // Naive Bayes threshold, default 0.65
    .nlpEnabled(true)                      // enable OpenNLP NER layer (opt-in)
    .nlpModelsDirectory(Path.of("..."))    // null = load from classpath
    .nlpConfidenceThreshold(0.75)          // OpenNLP min probability, default 0.70
    .enabledTypes(Set.of(PIIType.EMAIL,    // null / empty = all types
                         PIIType.SSN))
    .minimumSeverity(6)                    // 1–10; filter low-severity types
    .buildReverseMap(true)                 // disable for slight perf gain
    .heuristicEnabled(true)
    .mlEnabled(true)
    .build();
```

### Redaction Modes

| Mode | Example output | Use case |
|---|---|---|
| `TOKEN` | `[EMAIL_1]` | LLM pipelines — structure preserved |
| `MASK` | `█████████████████` | Logs, audit trails |
| `BLANK` | `[REDACTED]` | Human-readable reports |

---

## Architecture

```
Input text
    │
    ▼
┌──────────────────────────────────────────────────┐
│  Layer 1: HeuristicDetector                      │
│  Regex patterns + Luhn checksum + entropy filter │
│  SSN, Email, Phone, CC, IPs, API Keys, Passwords │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│  Layer 2: MLDetector                             │
│  Pure-Java Naive Bayes + FeatureExtractor        │
│  Person names, Organisations (context-aware)     │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│  Layer 3: NLPDetector  (optional, opt-in)        │
│  Apache OpenNLP NameFinderME (MaxEnt NER)        │
│  Multi-token person names, compound org names    │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│  CompositeDetector                               │
│  De-duplicate, resolve overlaps, HYBRID merging  │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│  PIITokenizer                                    │
│  TOKEN / MASK / BLANK + reverse map              │
└──────────────────────────────────────────────────┘
                      │
                      ▼
         RedactionResult  /  StreamRedactionSummary
```

For stream processing, `StreamProcessor` replaces the final step: lines are processed one at a time, redacted, and written immediately, keeping heap usage constant regardless of document size.

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
| **SPG Full (H + ML)** | **206,000 sentences/s** | **0%** |
| SPG Full + NLP | ~45,000 sentences/s* | 0% |

\* NLP throughput depends on model size and JVM warmup. Stream processing throughput is I/O-bound rather than CPU-bound. See [docs/benchmarks.md](docs/benchmarks.md) for full methodology.

---

## Building from Source

```bash
git clone https://github.com/Sushegaad/Semantic-Privacy-Guard.git
cd Semantic-Privacy-Guard

# Compile + test + coverage check (must be ≥ 80%)
mvn verify

# Run benchmarks
mvn test -P benchmark

# Build JAR only
mvn package -DskipTests
```

Requirements: JDK 17+ and Maven 3.8+.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Contributions especially welcome for:

- Additional OpenNLP model integrations (dates, locations)
- Additional training examples for the Naive Bayes corpus
- New PII type patterns (medical codes, national IDs)
- Performance benchmarks against real-world log datasets

---

## Security

See [SECURITY.md](SECURITY.md) for the CVE response process and responsible disclosure policy.

The base library has zero runtime dependencies, eliminating supply-chain attack vectors. OpenNLP is an optional dependency and is only loaded when explicitly configured. All regex patterns are validated against catastrophic backtracking (ReDoS).

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Copyright 2026 Hemant Naik / Sushegaad
