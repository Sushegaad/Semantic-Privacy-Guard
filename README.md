# 🛡 Semantic Privacy Guard

[![CI](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml/badge.svg)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sushegaad/semantic-privacy-guard?color=blue&logo=apache-maven)](https://central.sonatype.com/artifact/io.github.sushegaad/semantic-privacy-guard)
[![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-brightgreen)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Security Policy](https://img.shields.io/badge/security-policy-orange)](SECURITY.md)
[![Live Playground](https://img.shields.io/badge/playground-live-brightgreen)](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)

> **Open-source privacy firewall for AI applications.**
>
> Prevent sensitive data from reaching LLMs — without breaking prompts.

```
Input:   "Hi, I'm Alice Johnson. My SSN is 123-45-6789 and email is alice@acme.com."
Output:  "Hi, I'm [PERSON_NAME_1]. My SSN is [SSN_1] and email is [EMAIL_1]."
```

**[▶ Try it live in your browser →](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)** — paste any text, see instant results, nothing sent to any server.

---

## Table of Contents

- [Quick Start](#quick-start)
- [What It Detects](#what-it-detects)
- [Why SPG?](#why-spg)
- [Use Cases](#use-cases)
- [How It Works](#how-it-works)
- [Features](#features)
  - [Redaction Modes](#redaction-modes)
  - [Custom Pattern Registry](#custom-pattern-registry)
  - [JSON / XML Redaction](#json--xml-redaction)
  - [Stream-Based Processing](#stream-based-processing)
  - [Spring AI Integration](#spring-ai-integration)
  - [NLP Integration (Apache OpenNLP)](#nlp-integration-apache-opennlp)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Performance](#performance)
- [Building from Source](#building-from-source)
- [Getting Help](#getting-help)
- [Contributing](#contributing)
- [Security](#security)
- [License](#license)

---

## Quick Start

### Add the dependency

**Maven**
```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard</artifactId>
  <version>1.5.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'io.github.sushegaad:semantic-privacy-guard:1.5.0'
```

### Redact your first string

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

That's it. No API keys, no cloud calls, no configuration required.

---

## What It Detects

| Type | Example | Detection Method | Severity |
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
| `GENERIC_PII` | `EMP-042731` | Custom Pattern Registry | 5 |

---

## Why SPG?

### The problem with naive redaction

Most regex-based approaches fire on every title-cased word. SPG uses a three-layer pipeline to understand *context*, not just pattern shape:

```
"I ate an apple yesterday."          →  No match   ✓  (fruit, not a name)
"Contact Apple at (800) 275-2273."   →  [ORG_1] + [PHONE_1]  (company + phone)
"The Gospel of John has 21 chapters" →  No match   ✓  (literary reference)
"Dear John, your SSN is 123-45-6789" →  [PERSON_NAME_1] + [SSN_1]
"John Michael Smith confirmed."      →  [PERSON_NAME_1]  (OpenNLP NER)
```

### SPG vs. alternatives

| | **SPG** | Microsoft Presidio | Regex only |
|---|:---:|:---:|:---:|
| Runs fully offline | ✅ | ✅ | ✅ |
| Zero cloud cost | ✅ | ✅ | ✅ |
| Context-aware disambiguation | ✅ | ✅ | ❌ |
| Zero runtime dependencies | ✅ | ❌ | ✅ |
| Spring AI native adapter | ✅ | ❌ | ❌ |
| Stream / log file API | ✅ | ❌ | ❌ |
| Reverse map for de-tokenization | ✅ | ❌ | ❌ |
| Language | Java | Python | Any |

### Why not just use a cloud PII API?

Cloud PII APIs cost ~$0.001 per call. At 1 million prompts/day that is $1,000/day — and you are sending user data off-premise to perform the privacy check. SPG processes everything in-process, at $0/call, with no data leaving your network.

---

## Use Cases

**LLM API gateway** — Intercept every prompt at the gateway layer before it reaches OpenAI, Anthropic, or any third-party model. Employees can use ChatGPT or Copilot without accidentally leaking customer SSNs or email addresses.

**Log sanitization** — Scrub PII from application logs, access logs, and support tickets before they are stored or indexed. The stream API processes 50 MB log files at constant heap usage, one line at a time.

**Spring AI chatbot** — Drop `SPGAdvisor` into a Spring AI `ChatClient` in three lines. The advisor automatically redacts every prompt and stores a reverse map so the LLM's response can be de-tokenized for internal use.

**Compliance middleware** — The 2026 EU AI Act requires "Privacy by Design." SPG provides the interception layer between user input and any AI system, with an auditable match list and processing time for every call.

**Healthcare / finance data pipelines** — Register custom patterns (medical record numbers, employee IDs, policy numbers) via the Custom Pattern Registry and redact domain-specific identifiers alongside the built-in types.

---

## How It Works

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

Each layer catches what the others miss. When two layers agree on the same span the match is promoted to `DetectionSource.HYBRID` with elevated confidence. For stream processing, `StreamProcessor` replaces the final step — lines are processed one at a time and written immediately, keeping heap usage constant regardless of document size.

---

## Features

### Redaction Modes

| Mode | Example output | Use case |
|---|---|---|
| `TOKEN` | `[EMAIL_1]` | LLM pipelines — structure preserved, de-tokenizable |
| `MASK` | `█████████████████` | Logs, audit trails |
| `BLANK` | `[REDACTED]` | Human-readable reports |

```java
SPGConfig config = SPGConfig.builder()
    .redactionMode(RedactionMode.MASK)
    .build();
```

---

### Custom Pattern Registry

Register organisation-specific identifiers that built-in heuristics don't cover — employee IDs, medical record numbers, internal reference codes, or any proprietary format.

```java
SPGConfig config = SPGConfig.builder()
    .addPattern(PIIType.GENERIC_PII, "EMP-\\d{6}",          0.99, "Employee ID")
    .addPattern(PIIType.GENERIC_PII, "MRN-[A-Z0-9]{8}",     0.98, "Medical Record Number")
    .addPattern(PIIType.GENERIC_PII, "POL-[A-Z]{2}-\\d{8}", 0.97, "Policy Number")
    .build();

SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);

RedactionResult r = spg.redact(
    "Task EMP-042731 relates to policy POL-GB-00123456.");
// → "Task [PII_1] relates to policy [PII_2]."
```

Custom patterns are applied after all built-in patterns, so built-in matches always win for overlapping spans. Multiple calls to `.addPattern()` accumulate — they do not replace each other.

---

### JSON / XML Redaction

Redact PII directly inside structured documents. Text values are replaced in-place; keys, numbers, booleans, and markup structure are preserved exactly.

#### JSON

Requires `jackson-databind` on the classpath (not bundled):

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.17.0</version>
</dependency>
```

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

StructuredRedactionOutput out = spg.redactJson("""
    {
      "name": "Alice Johnson",
      "email": "alice@example.com",
      "account": 12345
    }
    """);

System.out.println(out.getRedactedContent());
// → {"name":"[PERSON_NAME_1]","email":"[EMAIL_1]","account":12345}

System.out.println(out.getMatchCount());   // → 2
System.out.println(out.getReverseMap());   // → {[PERSON_NAME_1]=Alice Johnson, [EMAIL_1]=alice@example.com}
```

#### XML

Uses JDK built-in `javax.xml` — no extra dependency required. XXE injection is hardened by disabling DOCTYPE declarations and external entity loading.

```java
StructuredRedactionOutput out = spg.redactXml("""
    <?xml version="1.0"?>
    <user>
      <name>Alice Johnson</name>
      <email>alice@example.com</email>
      <id>12345</id>
    </user>
    """);

System.out.println(out.getRedactedContent());
// → <?xml version="1.0"?><user><name>[PERSON_NAME_1]</name><email>[EMAIL_1]</email><id>12345</id></user>
```

`StructuredRedactionOutput` fields:

| Method | Returns |
|---|---|
| `getRedactedContent()` | Redacted JSON or XML string |
| `getReverseMap()` | `Map<String, String>` token → original value |
| `getMatchCount()` | Total PII matches found |
| `hasPII()` | `true` if any PII was detected |

---

### Stream-Based Processing

Loading a 50 MB log file into a `String` costs ~150–200 MB of heap per concurrent request. On a Lambda with 512 MB RAM and 10 concurrent calls that is an OOM event. `StreamProcessor` processes one line at a time — heap stays bounded by the longest single line, typically under 4 KB.

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

### Spring AI Integration

The `semantic-privacy-guard-spring-ai` adapter registers a Spring AI `CallAroundAdvisor` that automatically redacts PII from every prompt before it reaches the LLM.

#### Add the dependency

```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard-spring-ai</artifactId>
  <version>1.5.0</version>
</dependency>
```

#### Three-line usage

```java
import com.semanticprivacyguard.SemanticPrivacyGuard;
import com.semanticprivacyguard.springai.SPGAdvisor;
import org.springframework.ai.chat.client.ChatClient;

ChatClient client = ChatClient.builder(chatModel)
    .defaultAdvisors(new SPGAdvisor(SemanticPrivacyGuard.create()))
    .build();

// PII is now automatically redacted before every call
String reply = client.prompt()
    .user("My SSN is 123-45-6789, can you help?")
    .call()
    .content();
// The LLM receives: "My SSN is [SSN_1], can you help?"
```

#### Auto-configuration (Spring Boot)

Drop the dependency on the classpath and Spring Boot wires everything automatically. No code changes required. Tune behaviour via `application.properties`:

```properties
# Enable / disable the advisor entirely (default: true)
spg.enabled=true

# Redaction mode: TOKEN (default), MASK, or BLANK
spg.redaction-mode=TOKEN

# Naive Bayes confidence threshold (default: 0.65)
spg.ml-confidence-threshold=0.65

# Minimum PII severity to redact (1–10; default: 1 = all types)
# Use 6 to focus on email, phone, SSN, credit card and skip IP / org
spg.minimum-severity=1

# Whether to also redact the system prompt (default: false)
spg.redact-system-prompt=false

# Spring advisor chain order — lower = earlier (default: Integer.MIN_VALUE + 100)
spg.advisor-order=-2147483548
```

#### Accessing the reverse map

The advisor stores the token-to-original-value reverse map in the advisor context under `"spg.reverseMap"` so downstream components can de-tokenize LLM responses:

```java
@SuppressWarnings("unchecked")
Map<String, String> reverseMap =
    (Map<String, String>) advisedRequest.adviseContext().get(SPGAdvisor.REVERSE_MAP_CONTEXT_KEY);
```

Full response de-tokenization via `SemanticPrivacyGuard.detokenize()` is planned for v1.6.0.

<details>
<summary>Advanced: overriding the auto-configured bean</summary>

Declare your own `SemanticPrivacyGuard` or `SPGAdvisor` bean and the auto-configuration backs off automatically:

```java
@Configuration
public class MyPrivacyConfig {

    /**
     * Custom guard: NLP enabled, employee-ID pattern, high-severity only.
     * Auto-configuration backs off because this bean is present.
     */
    @Bean
    public SemanticPrivacyGuard semanticPrivacyGuard() {
        SPGConfig config = SPGConfig.builder()
            .redactionMode(RedactionMode.TOKEN)
            .nlpEnabled(true)
            .minimumSeverity(6)
            .addPattern(PIIType.GENERIC_PII, "EMP-\\d{6}", 0.99, "Employee ID")
            .build();
        return SemanticPrivacyGuard.create(config);
    }

    /**
     * Custom advisor: also redact the system prompt, run first in chain.
     */
    @Bean
    public SPGAdvisor spgAdvisor(SemanticPrivacyGuard spg) {
        return new SPGAdvisor(spg, /* redactSystemPrompt= */ true, Ordered.HIGHEST_PRECEDENCE);
    }
}
```

</details>

---

### NLP Integration (Apache OpenNLP)

The third detection layer uses Apache OpenNLP Named Entity Recognition — a Maximum Entropy model trained on large NLP corpora. It excels at cases the Naive Bayes layer struggles with: multi-token person names, compound organisation names, and names in varied syntactic positions.

#### Enable NLP

```java
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .nlpConfidenceThreshold(0.75)   // default 0.70
    .build();

SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);
```

#### NLP detection types

| Detected by OpenNLP | PIIType | Notes |
|---|---|---|
| Person names | `PERSON_NAME` | Multi-token names, varied positions |
| Organisation names | `ORGANIZATION` | Compound names, acronyms |

NLP results flow through the same `CompositeDetector` de-duplication as heuristic and ML results. When two layers agree on the same span the match is promoted to `DetectionSource.HYBRID` with the higher confidence score.

<details>
<summary>NLP setup — model download and classpath configuration</summary>

OpenNLP models are large binary files not bundled in the JAR. Download them from the [Apache OpenNLP model repository](https://opennlp.sourceforge.net/models-1.5/):

```
en-ner-person.bin        (required, ~14 MB)    — person name NER
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
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .nlpModelsDirectory(Path.of("/opt/nlp-models"))
    .build();
```

Add the OpenNLP runtime dependency (marked `optional` in SPG — you must add it yourself):

```xml
<dependency>
  <groupId>org.apache.opennlp</groupId>
  <artifactId>opennlp-tools</artifactId>
  <version>2.3.3</version>
</dependency>
```

**Thread safety:** `NameFinderME` is not thread-safe. `NLPDetector` uses `ThreadLocal` to give each thread its own `NameFinderME` wrapper, all sharing the same immutable `TokenNameFinderModel`. Adaptive state is cleared after every `detect()` call. The class is safe under Java 21+ virtual threads (Project Loom).

</details>

---

## Configuration

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
    .addPattern(PIIType.GENERIC_PII, "EMP-\\d{6}", 0.99, "Employee ID")
    .build();
```

<details>
<summary>Virtual threads (Project Loom / Java 21+)</summary>

SPG is stateless and thread-safe by design. On Java 21+ it scales naturally across virtual threads with zero contention:

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

</details>

---

## API Reference

### `SemanticPrivacyGuard`

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();        // defaults
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create(config);  // custom
```

| Method | Returns | Description |
|---|---|---|
| `redact(String text)` | `RedactionResult` | Full detection + replacement pass |
| `containsPII(String text)` | `boolean` | Fast pre-flight check (~30% faster than `redact()`) |
| `analyse(String text)` | `List<PIIMatch>` | Detection without redaction — for audit pipelines |
| `redactJson(String json)` | `StructuredRedactionOutput` | Redacts string values inside a JSON document |
| `redactXml(String xml)` | `StructuredRedactionOutput` | Redacts text nodes and attributes inside an XML document |
| `redactStream(InputStream, OutputStream)` | `StreamRedactionSummary` | Stream redaction (UTF-8) |
| `redactStream(Reader, Writer)` | `StreamRedactionSummary` | Stream redaction (character streams) |
| `redactPath(Path, Path)` | `StreamRedactionSummary` | File-to-file redaction |
| `streamProcessor()` | `StreamProcessor` | Access the full stream processor for `redactLines(Stream<String>)` |

### `RedactionResult`

| Method | Returns |
|---|---|
| `getRedactedText()` | Sanitised text with PII replaced by tokens |
| `getOriginalText()` | The unmodified input |
| `getMatches()` | Unmodifiable `List<PIIMatch>` sorted by position |
| `getReverseMap()` | `Map<String, String>` token → original value |
| `getMatchCount()` | Number of PII items detected |
| `containsPII()` | `true` if at least one item was detected |
| `isClean()` | `true` if no PII was detected |
| `getProcessingTimeMs()` | Wall-clock processing time in milliseconds |

---

## Performance

| Approach | Throughput | Macro F1 | False Positives |
|---|---|---|---|
| Naive regex (2 patterns) | 580,000 sentences/s | — | ~60% of clean sentences |
| SPG Heuristic-only | 390,000 sentences/s | 0.87 | 20% |
| **SPG Full (Heuristic + ML)** | **206,000 sentences/s** | **0.93** | **0%** |
| SPG Full + NLP | ~45,000 sentences/s* | — | 0% |

\* NLP throughput depends on model size and JVM warmup. Stream processing throughput is I/O-bound rather than CPU-bound.

See the **[live benchmark page](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/benchmarks.html)** for full precision/recall/F1 numbers, or regenerate results against your own hardware:

```bash
mvn test -P benchmark
```

---

## Building from Source

```bash
git clone https://github.com/Sushegaad/Semantic-Privacy-Guard.git
cd Semantic-Privacy-Guard

# Compile + test + coverage check (must be ≥ 80%)
mvn verify

# Run benchmarks and regenerate docs/benchmark-results.json
mvn test -P benchmark

# Build JAR only
mvn package -DskipTests
```

Requirements: JDK 17+ and Maven 3.8+.

---

## Getting Help

- **Bug reports and feature requests** — [open an issue](https://github.com/Sushegaad/Semantic-Privacy-Guard/issues)
- **Questions and discussion** — [GitHub Discussions](https://github.com/Sushegaad/Semantic-Privacy-Guard/discussions)
- **Security vulnerabilities** — see [SECURITY.md](SECURITY.md) for responsible disclosure

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
