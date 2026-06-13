# Semantic Privacy Guard

[![CI](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml/badge.svg)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sushegaad/semantic-privacy-guard?color=blue&logo=apache-maven)](https://central.sonatype.com/artifact/io.github.sushegaad/semantic-privacy-guard)
[![Coverage](https://img.shields.io/badge/coverage-%E2%89%A580%25-brightgreen)](https://github.com/Sushegaad/Semantic-Privacy-Guard/actions)
[![Java](https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
[![Security Policy](https://img.shields.io/badge/security-policy-orange)](SECURITY.md)
[![Live Playground](https://img.shields.io/badge/playground-live-brightgreen)](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)

**AI Privacy Firewall for Java.** Detect and redact sensitive data before it reaches ChatGPT, Claude, Gemini, MCP tools, RAG pipelines, or any internal AI system — entirely on-prem, with zero cloud dependencies.

```
Input:   "Hi, I'm Alice Johnson. My SSN is 123-45-6789 and email is alice@acme.com."
Output:  "Hi, I'm [PERSON_NAME_1]. My SSN is [SSN_1] and email is [EMAIL_1]."
```

**[▶ Try it live in your browser →](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/index.html)** — no sign-up, nothing sent to any server.

![SPG Playground Demo](docs/playground-demo.gif)

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
  - [Spring Boot HTTP Filter](#spring-boot-http-filter)
  - [LLM Gateway Demo](#llm-gateway-demo)
  - [NLP Integration (Apache OpenNLP)](#nlp-integration-apache-opennlp)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Performance](#performance)
- [What's New in v1.6.0](#whats-new-in-v160)
- [Building from Source](#building-from-source)
- [Getting Help](#getting-help)
- [Security](#security)
- [License](#license)

---

## Quick Start

Add one dependency — no API keys, no accounts, no configuration required:

**Maven**
```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard</artifactId>
  <version>1.6.0</version>
</dependency>
```

**Gradle**
```groovy
implementation 'io.github.sushegaad:semantic-privacy-guard:1.6.0'
```

Redact your first string:

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

RedactionResult result = spg.redact(
    "Email Alice at alice.doe@acme.com or call (555) 867-5309. SSN: 123-45-6789."
);

System.out.println(result.getRedactedText());
// → "Email [PERSON_NAME_1] at [EMAIL_1] or call [PHONE_1]. SSN: [SSN_1]."

System.out.println(result.getMatchCount());       // → 4
System.out.println(result.getProcessingTimeMs()); // → < 1 ms
```

That's all you need to start intercepting PII before it reaches an LLM.

---

## What It Detects

| Type | Example | Detection Method | Severity |
|---|---|---|---|
| `SSN` | `123-45-6789` | Regex + exclusion rules | 10 |
| `CREDIT_CARD` | `4532 0151 1283 0366` | Regex + Luhn checksum | 10 |
| `API_KEY` | `AKIAIOSFODNN7EXAMPLE` | Regex + entropy filter | 9 |
| `PASSWORD` | `password=MyS3cr3t` | Regex (keyword-prefixed) | 9 |
| `BANK_ACCOUNT` | `GB29NWBK60161331926819` | Regex (IBAN) | 8 |
| `EMAIL` | `alice@example.com` | Regex | 6 |
| `PHONE` | `(555) 867-5309` | Regex (NANP validated) | 6 |
| `PERSON_NAME` | `Alice Johnson` | Naive Bayes ML + OpenNLP NER | 6 |
| `DATE_OF_BIRTH` | `dob: 03/15/1985` | Regex (context-prefixed) | 6 |
| `IP_ADDRESS` | `192.168.1.100` | Regex (range-validated) | 4 |
| `ORGANIZATION` | `Barclays Bank PLC` | Naive Bayes ML + OpenNLP NER | 3 |
| `GENERIC_PII` | `EMP-042731` | Custom Pattern Registry | configurable |

---

## Why SPG?

### The problem with naive redaction

Most regex-based approaches flag every title-cased word. SPG uses a three-layer pipeline that understands *context*, not just shape:

```
"I ate an apple yesterday."          →  No match      ✓  (fruit, not a name)
"Contact Apple at (800) 275-2273."   →  [ORG_1] + [PHONE_1]
"The Gospel of John has 21 chapters" →  No match      ✓  (literary reference)
"Dear John, your SSN is 123-45-6789" →  [PERSON_NAME_1] + [SSN_1]
```

### The problem with cloud PII APIs

Cloud PII detection costs ~$0.001 per call. At one million prompts per day that is **$1,000/day** — and you are sending user data off-premise to perform the privacy check. SPG processes everything in-process at **$0/call** with no data leaving your network.

### SPG vs. alternatives

| | **SPG** | Microsoft Presidio | Regex only |
|---|:---:|:---:|:---:|
| Runs fully offline | ✅ | ✅ | ✅ |
| Zero cloud cost | ✅ | ✅ | ✅ |
| Context-aware disambiguation | ✅ | ✅ | ❌ |
| Zero runtime dependencies | ✅ | ❌ | ✅ |
| Spring AI native adapter | ✅ | ❌ | ❌ |
| Spring Boot HTTP filter | ✅ | ❌ | ❌ |
| Stream / log file API | ✅ | ❌ | ❌ |
| Reverse map for de-tokenization | ✅ | ❌ | ❌ |
| Language | **Java** | Python | Any |

---

## Use Cases

**LLM API gateway** — Intercept every prompt at the gateway layer before it reaches OpenAI, Anthropic, or any third-party model. Employees can use ChatGPT or Copilot without accidentally leaking customer SSNs or email addresses.

**Log sanitization** — Scrub PII from application logs, access logs, and support tickets before they are stored or indexed. The stream API processes 50 MB log files at constant heap usage, one line at a time.

**Spring AI chatbot** — Drop `SPGAdvisor` into a Spring AI `ChatClient` in three lines. The advisor automatically redacts every prompt and stores a reverse map so the LLM's response can be de-tokenized for internal use.

**Healthcare / finance data pipelines** — Register custom patterns (medical record numbers, employee IDs, policy numbers) via the Custom Pattern Registry and redact domain-specific identifiers alongside the built-in types.

**Compliance middleware** — The EU AI Act and GDPR require privacy controls on AI inputs. SPG provides an auditable interception layer between user input and any AI system, with match list and processing time for every call.

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

Each layer catches what the others miss. When two layers agree on the same span the match is promoted to `DetectionSource.HYBRID` with elevated confidence. `StreamProcessor` replaces the final step for large files — lines are processed one at a time, keeping heap usage constant regardless of document size.

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

Custom patterns are applied after all built-in patterns. Multiple `.addPattern()` calls accumulate — they do not replace each other.

---

### JSON / XML Redaction

Redact PII directly inside structured documents. Text values are replaced in-place; keys, numbers, booleans, and markup structure are preserved exactly.

**JSON** — requires `jackson-databind` on the classpath:

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
  <version>2.17.0</version>
</dependency>
```

```java
StructuredRedactionOutput out = spg.redactJson("""
    {
      "name": "Alice Johnson",
      "email": "alice@example.com",
      "account": 12345
    }
    """);

System.out.println(out.getRedactedContent());
// → {"name":"[PERSON_NAME_1]","email":"[EMAIL_1]","account":12345}
```

**XML** — uses JDK built-in `javax.xml`, no extra dependency. XXE-hardened by default:

```java
StructuredRedactionOutput out = spg.redactXml("""
    <?xml version="1.0"?>
    <user>
      <name>Alice Johnson</name>
      <email>alice@example.com</email>
      <id>12345</id>
    </user>
    """);
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

Loading a 50 MB log file into a `String` costs ~150–200 MB of heap per concurrent request. `StreamProcessor` processes one line at a time — heap stays bounded by the longest single line, typically under 4 KB.

```java
SemanticPrivacyGuard spg = SemanticPrivacyGuard.create();

// File-to-file: constant heap regardless of file size
StreamRedactionSummary summary =
    spg.redactPath(Path.of("access.log"), Path.of("access.clean.log"));
// → StreamRedactionSummary[lines=84231, linesWithPII=312, matches=389, timeMs=740]

// InputStream / OutputStream (servlet filter)
spg.redactStream(request.getInputStream(), response.getOutputStream());

// Lazy Java Stream — integrates with Files.lines()
try (Stream<String> lines = Files.lines(inputPath)) {
    spg.streamProcessor()
       .redactLines(lines)
       .forEach(outputWriter::println);
}
```

Token counters are document-scoped: `[EMAIL_1]` on line 3 and `[EMAIL_2]` on line 7 — never two `[EMAIL_1]` tokens in the same document.

---

### Spring AI Integration

The `semantic-privacy-guard-spring-ai` adapter registers a Spring AI `CallAroundAdvisor` that automatically redacts PII from every prompt before it reaches the LLM.

**Add the dependency:**

```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard-spring-ai</artifactId>
  <version>1.6.0</version>
</dependency>
```

**Three-line usage:**

```java
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

**Auto-configuration (Spring Boot)** — drop the dependency on the classpath and Spring Boot wires everything automatically. Tune via `application.properties`:

```properties
spg.enabled=true
spg.redaction-mode=TOKEN
spg.ml-confidence-threshold=0.65
spg.minimum-severity=1
spg.redact-system-prompt=false
```

**Accessing the reverse map:**

```java
@SuppressWarnings("unchecked")
Map<String, String> reverseMap =
    (Map<String, String>) advisedRequest.adviseContext().get(SPGAdvisor.REVERSE_MAP_CONTEXT_KEY);
```

<details>
<summary>Advanced: overriding the auto-configured bean</summary>

```java
@Configuration
public class MyPrivacyConfig {

    @Bean
    public SemanticPrivacyGuard semanticPrivacyGuard() {
        return SemanticPrivacyGuard.create(SPGConfig.builder()
            .nlpEnabled(true)
            .minimumSeverity(6)
            .addPattern(PIIType.GENERIC_PII, "EMP-\\d{6}", 0.99, "Employee ID")
            .build());
    }

    @Bean
    public SPGAdvisor spgAdvisor(SemanticPrivacyGuard spg) {
        return new SPGAdvisor(spg, /* redactSystemPrompt= */ true, Ordered.HIGHEST_PRECEDENCE);
    }
}
```

</details>

---

### Spring Boot HTTP Filter

The `semantic-privacy-guard-spring-boot-filter` module wraps SPG as a servlet `Filter`, redacting PII from JSON request and response bodies on every HTTP call.

**Add the dependency:**

```xml
<dependency>
  <groupId>io.github.sushegaad</groupId>
  <artifactId>semantic-privacy-guard-spring-boot-filter</artifactId>
  <version>1.6.0</version>
</dependency>
```

Drop the JAR on the classpath — no code changes required. The filter auto-configures and covers all paths by default.

**Configuration:**

```properties
spg.filter.enabled=true
spg.filter.redact-request-body=true
spg.filter.redact-response-body=true
spg.filter.included-paths=/**
spg.filter.excluded-paths=/actuator/**,/health
spg.filter.redaction-mode=TOKEN
spg.filter.minimum-severity=1
```

**Manual registration:**

```java
@Bean
public FilterRegistrationBean<SPGRequestFilter> spgFilter(
        SemanticPrivacyGuard spg, SPGFilterProperties props) {
    var reg = new FilterRegistrationBean<>(new SPGRequestFilter(spg, props));
    reg.addUrlPatterns("/api/*");
    reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return reg;
}
```

---

### LLM Gateway Demo

`examples/llm-gateway-demo` is a self-contained Spring Boot application demonstrating the full privacy-firewall round-trip:

```
Incoming prompt (with PII)
        │
        ▼ spg.redact()
Sanitised prompt ([EMAIL_1], [SSN_1] …)
        │
        ▼ LLM API call
Raw LLM response (tokens echoed verbatim)
        │
        ▼ de-tokenize via reverse map
Final response (original values restored)
```

**Run it — no API key needed:**

```bash
cd examples/llm-gateway-demo
mvn spring-boot:run
```

```bash
curl -s -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt": "My name is Alice Johnson and email is alice@corp.com. Summarise my profile."}' \
  | python3 -m json.tool
```

The demo uses a built-in stub LLM by default. To use a real OpenAI-compatible model, set `llm.api-key` in `application.properties`.

---

### NLP Integration (Apache OpenNLP)

The third detection layer uses Apache OpenNLP Named Entity Recognition — a Maximum Entropy model trained on large NLP corpora. It excels at multi-token person names, compound organisation names, and names in varied syntactic positions.

**Enable NLP:**

```java
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .nlpConfidenceThreshold(0.75)
    .build();
```

| Detected by OpenNLP | PIIType | Notes |
|---|---|---|
| Person names | `PERSON_NAME` | Multi-token names, varied positions |
| Organisation names | `ORGANIZATION` | Compound names, acronyms |

NLP results flow through the same `CompositeDetector` de-duplication as heuristic and ML results. Spans matched by multiple layers are promoted to `DetectionSource.HYBRID`.

<details>
<summary>NLP setup — model download and classpath configuration</summary>

OpenNLP models are not bundled in the JAR. Download from the [Apache OpenNLP model repository](https://opennlp.sourceforge.net/models-1.5/):

```
en-ner-person.bin        (~14 MB)  — person name NER
en-ner-organization.bin  (~16 MB)  — organisation name NER
en-token.bin             (~1 MB)   — MaxEnt tokenizer
```

Place them on the classpath at `src/main/resources/models/`, or point to a directory:

```java
SPGConfig config = SPGConfig.builder()
    .nlpEnabled(true)
    .nlpModelsDirectory(Path.of("/opt/nlp-models"))
    .build();
```

Add the OpenNLP runtime dependency (marked `optional` in SPG):

```xml
<dependency>
  <groupId>org.apache.opennlp</groupId>
  <artifactId>opennlp-tools</artifactId>
  <version>2.3.3</version>
</dependency>
```

`NLPDetector` uses `ThreadLocal` to give each thread its own `NameFinderME` instance sharing the same immutable model. Safe under Java 21+ virtual threads.

</details>

---

## Configuration

```java
SPGConfig config = SPGConfig.builder()
    .redactionMode(RedactionMode.TOKEN)    // TOKEN | MASK | BLANK
    .mlConfidenceThreshold(0.70)           // Naive Bayes threshold, default 0.65
    .nlpEnabled(true)                      // enable OpenNLP NER (opt-in)
    .nlpModelsDirectory(Path.of("..."))    // null = load from classpath
    .nlpConfidenceThreshold(0.75)          // OpenNLP min probability, default 0.70
    .enabledTypes(Set.of(PIIType.EMAIL,    // null = all types enabled
                         PIIType.SSN))
    .minimumSeverity(6)                    // filter types below this severity (1–10)
    .buildReverseMap(true)                 // disable for slight perf gain
    .heuristicEnabled(true)
    .mlEnabled(true)
    .addPattern(PIIType.GENERIC_PII, "EMP-\\d{6}", 0.99, "Employee ID")
    .build();
```

<details>
<summary>Virtual threads (Java 21+)</summary>

SPG is stateless and thread-safe. On Java 21+ it scales naturally across virtual threads:

```java
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
| `redact(String)` | `RedactionResult` | Full detection + replacement pass |
| `containsPII(String)` | `boolean` | Fast pre-flight check (~30% faster than `redact()`) |
| `analyse(String)` | `List<PIIMatch>` | Detection without redaction — for audit pipelines |
| `redactJson(String)` | `StructuredRedactionOutput` | Redacts string values inside a JSON document |
| `redactXml(String)` | `StructuredRedactionOutput` | Redacts text nodes inside an XML document |
| `redactStream(InputStream, OutputStream)` | `StreamRedactionSummary` | Stream redaction (UTF-8) |
| `redactStream(Reader, Writer)` | `StreamRedactionSummary` | Stream redaction (character streams) |
| `redactPath(Path, Path)` | `StreamRedactionSummary` | File-to-file redaction |
| `streamProcessor()` | `StreamProcessor` | Access stream processor for `redactLines(Stream<String>)` |

### `RedactionResult`

| Method | Returns |
|---|---|
| `getRedactedText()` | Sanitised text with PII replaced by tokens |
| `getOriginalText()` | The unmodified input |
| `getMatches()` | `List<PIIMatch>` sorted by position |
| `getReverseMap()` | `Map<String, String>` token → original value |
| `getMatchCount()` | Number of PII items detected |
| `containsPII()` | `true` if at least one item was detected |
| `isClean()` | `true` if no PII was detected |
| `getProcessingTimeMs()` | Wall-clock processing time in milliseconds |

---

## Performance

| Approach | Throughput | Macro F1 | Notes |
|---|---|---|---|
| Naive regex (2 patterns) | ~580K sentences/s | — | ~60% false positive rate on clean sentences |
| SPG Heuristic-only | ~390K sentences/s | 0.87 | Regex + Luhn + entropy, no ML |
| **SPG Full (Heuristic + ML)** | **~206K sentences/s** | **0.93** | Default configuration |
| SPG Full + NLP | ~45K sentences/s | — | NLP throughput is model- and JVM-warmup-dependent |

Stream processing throughput is I/O-bound rather than CPU-bound.

See the **[live benchmark page](https://sushegaad.github.io/Semantic-Privacy-Guard/docs/benchmarks.html)** for full precision/recall/F1 numbers, or regenerate results on your hardware:

```bash
mvn test -P benchmark
```

---

## What's New in v1.6.0

- **Website overhaul** — playground redesigned as a developer product showcase with side-by-side entity highlighting, detection dashboard, real-world scenario prompts, and LLM-ready badges
- **Detailed playground scenarios** — four realistic multi-PII prompts (customer support ticket, healthcare record, enterprise HR, P1 incident report) that exercise 6–10 PII types each
- **Version consistency** — all modules (`spring-ai`, `spring-boot-filter`, `llm-gateway-demo`) aligned to 1.6.0 in lockstep
- **Benchmark harness documented** — `BenchmarkTest` and `SyntheticDataset` fully documented and runnable via `mvn test -P benchmark`

**Previous releases:** [v1.5.0](https://github.com/Sushegaad/Semantic-Privacy-Guard/releases/tag/v1.5.0) · [v1.4.0](https://github.com/Sushegaad/Semantic-Privacy-Guard/releases/tag/v1.4.0)

---

## Building from Source

```bash
git clone https://github.com/Sushegaad/Semantic-Privacy-Guard.git
cd Semantic-Privacy-Guard

# Compile + test + coverage check (≥ 80% required)
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

## Security

See [SECURITY.md](SECURITY.md) for the CVE response process and responsible disclosure policy.

The base library has zero runtime dependencies, eliminating supply-chain attack vectors. OpenNLP is an optional dependency loaded only when explicitly configured. All regex patterns are validated against catastrophic backtracking (ReDoS).

---

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Copyright 2026 Hemant Naik / Sushegaad
