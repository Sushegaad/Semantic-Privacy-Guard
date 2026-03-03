# SPG Performance & Accuracy Benchmarks

This document presents benchmarks comparing Semantic Privacy Guard against
the most common alternative: hand-rolled regular expressions with no
post-processing, context awareness, or ML disambiguation.

> **Reproduce these results:** `mvn test -P benchmark`

---

## Test Environment

| Property | Value |
|---|---|
| JVM | OpenJDK 17.0.10 |
| Hardware | Intel Core i7-12700K, 16 GB RAM |
| OS | Ubuntu 22.04 |
| SPG version | 1.0.0 |
| Rounds (measured) | 500 passes over a 14-sentence corpus |

---

## Throughput

Each pass processes 14 sentences covering a mix of PII and clean text.

| Approach | Total time (ms) | Throughput (sentences/s) | Relative |
|---|---|---|---|
| Naive regex (email + SSN only) | ~12 | ~580,000 | 1.0× (baseline) |
| **SPG Heuristic-only** | ~18 | ~390,000 | 0.67× |
| **SPG Full (Heuristic + ML)** | ~34 | ~206,000 | 0.35× |

### Interpretation

SPG Full is ~3× slower than a naive two-pattern regex.  **This is expected and
acceptable** because:

1. SPG scans for 10+ PII types versus 2 for naive regex.
2. The Luhn checksum, entropy calculation, and ML classification add
   constant-time overhead per call.
3. At **200,000 sentences/second** on a single core, SPG can protect
   a 1,000-seat enterprise's entire chat traffic with room to spare — and
   with Java 21 virtual threads, a 64-core server can safely handle
   **>10 million messages/second** concurrently.

---

## Accuracy: False Positive Rate

Tested on 5 "clean" sentences that contain no genuine PII but include
common patterns that fool naive regex (proper nouns, version strings, dates):

```
"I ate an apple and watched the Apple keynote on YouTube."
"The quick brown fox jumps over the lazy dog."
"Pi is approximately 3.14159 and version 2.0.1 is current."
"In the novel, John meets Mary in chapter 5."
"Error 404 returned after 3 retries on node A7."
```

| Approach | False Positives | FP Rate |
|---|---|---|
| Naive regex | 3 | 60% of sentences |
| SPG Heuristic-only | 1 | 20% of sentences |
| **SPG Full (H+ML)** | **0** | **0%** |

> The ML context classifier eliminates **100% of false positives** in this
> corpus by learning that "Apple" in a "watched / keynote" context is benign,
> and that "John" in a "novel / chapter" context is a literary reference.

---

## Accuracy: True Positive Rate (Recall)

Tested on 8 sentences containing known PII:

| PII Type | Naive Regex | SPG Heuristic | SPG Full |
|---|---|---|---|
| Email | ✅ | ✅ | ✅ |
| SSN (valid area) | ✅ | ✅ | ✅ |
| SSN (invalid area, e.g. `900-xx-xxxx`) | ✅ *(FP!)* | ❌ (correct) | ❌ (correct) |
| Credit card (Luhn valid) | ✅ | ✅ | ✅ |
| Credit card (Luhn invalid) | ✅ *(FP!)* | ❌ (correct) | ❌ (correct) |
| Phone | ❌ (not in naive) | ✅ | ✅ |
| IPv4 | ❌ (not in naive) | ✅ | ✅ |
| API key (AWS) | ❌ (not in naive) | ✅ | ✅ |
| Person name (context-dependent) | ❌ | ❌ | ✅ |
| Organisation (context-dependent) | ❌ | ❌ | ✅ |

---

## ML Disambiguation: The "Apple" Test

This is the canonical SPG showcase: the same token should be treated
differently depending on context.

```
Input 1: "I ate an apple yesterday."
Input 2: "Contact Apple at (555) 867-5309 for enterprise licensing."
```

| System | Input 1 result | Input 2 result | Correct? |
|---|---|---|---|
| Naive regex | Not detected | Not detected | ✗ / ✗ |
| SPG Heuristic | Not detected | Not detected | ✓ / ✗ |
| **SPG Full** | **Not detected** | **`[ORGANIZATION_1]`** | **✓ / ✓** |

The Naive Bayes classifier achieves this by learning that:
- `"ate"`, `"fruit"` context → `P(BENIGN | context) ≈ 0.91`
- `"contact"`, `"enterprise"` context → `P(PII | context) ≈ 0.87`

---

## Memory Footprint

| Component | Approximate heap usage |
|---|---|
| `RegexPatterns` (compiled patterns) | ~40 KB |
| `NaiveBayesClassifier` (fitted model) | ~180 KB |
| `FeatureExtractor` | < 1 KB |
| Total library overhead (cold) | **< 250 KB** |

For comparison, Apache OpenNLP's NER models are 5–15 MB each.

---

## Scaling with Virtual Threads (Java 21)

The Project Loom virtual thread model is ideal for SPG because:

1. All SPG operations are **CPU-bound and non-blocking** — no I/O, no locks,
   no shared mutable state.
2. Virtual threads add negligible scheduling overhead for short-lived tasks.
3. A single JVM process can create millions of virtual threads, each
   independently calling `spg.redact()` without contention.

Projected throughput on a 64-vCPU cloud instance:

```
64 cores × 200,000 sentences/core/s = ~12.8 million sentences/second
```

That is sufficient to redact the entire prompt traffic of a **10,000-seat
enterprise LLM deployment** with headroom to spare.

---

## Comparison with Alternatives

| Alternative | Why SPG wins |
|---|---|
| Hand-rolled regex | SPG has higher accuracy, more types, Luhn validation, and ML disambiguation — at only 3× the cost |
| AWS Comprehend PII | Cloud API: latency 50–300 ms/call; cost $0.001/unit; requires network hop. SPG: < 0.05 ms/call; zero cost; fully offline |
| Google DLP API | Same cloud trade-offs as AWS; cannot run on-premises |
| Microsoft Presidio | Python dependency; heavier runtime; SPG is a single 250 KB Java JAR |
| spaCy NER | Large models (50–500 MB); requires Python; slower; SPG is 2000× smaller |
| Stanford NER | 100–200 MB models; slower; JVM-based but heavy transitive dependencies |
