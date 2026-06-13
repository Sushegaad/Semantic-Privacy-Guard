# Awesome-List Submission Drafts

Ready-to-submit PR descriptions for three lists. Fork each repo, add the line,
and paste the relevant PR body below.

---

## 1. awesome-java

**Repo:** https://github.com/akullpp/awesome-java  
**Section to add under:** `Security`  
**Line to insert** (alphabetically within Security):

```markdown
- [Semantic Privacy Guard](https://github.com/Sushegaad/Semantic-Privacy-Guard) - Offline PII detection and redaction library combining regex heuristics, Naive Bayes ML, and Apache OpenNLP NER. Zero cloud cost, Spring AI and Spring Boot native integration.
```

**PR Title:** `Add Semantic Privacy Guard to Security section`

**PR Body:**
```
## Add Semantic Privacy Guard

**Link:** https://github.com/Sushegaad/Semantic-Privacy-Guard  
**Category:** Security

### Why it belongs here

Semantic Privacy Guard (SPG) is a Java 17+ library for offline PII detection
and redaction. It uses a three-layer pipeline (regex heuristics + Naive Bayes
ML + optional Apache OpenNLP NER) to detect names, emails, SSNs, phone numbers,
credit cards, IBANs, API keys, and more.

Key properties that make it relevant to this list:
- Pure Java, zero runtime cloud dependencies
- 206,000+ sentences/second throughput (heuristic + ML mode)
- Spring AI `CallAroundAdvisor` for automatic LLM prompt redaction
- Spring Boot servlet filter for HTTP request/response redaction
- Stream API for processing log files with constant heap usage
- Reverse-map de-tokenization for round-trip privacy
- Published to Maven Central (`io.github.sushegaad:semantic-privacy-guard`)
- Apache 2.0 licensed

I am the author of this library.
```

---

## 2. awesome-privacy

**Repo:** https://github.com/pluja/awesome-privacy  
**Section to add under:** `Developer Tools` or `Security Tools`  
**Line to insert:**

```markdown
- [Semantic Privacy Guard](https://github.com/Sushegaad/Semantic-Privacy-Guard) - Open-source Java library for offline PII detection and redaction. Combines regex, Naive Bayes ML, and NLP — no data ever leaves the JVM.
```

**PR Title:** `Add Semantic Privacy Guard – Java PII redaction library`

**PR Body:**
```
## Add Semantic Privacy Guard

**Link:** https://github.com/Sushegaad/Semantic-Privacy-Guard  
**Why privacy-friendly:** All processing is 100% local. No text is ever sent
to a third-party API or cloud service. The library runs entirely within the
JVM with zero network calls required.

**What it does:**
- Detects PII (names, emails, phones, SSNs, credit cards, IBANs, API keys)
  using a hybrid heuristic + Naive Bayes ML pipeline
- Redacts PII by replacing it with structured tokens (`[EMAIL_1]`, `[SSN_1]`)
- Supports de-tokenization — reverse mapping to restore originals
- First-class Spring Boot and Spring AI integrations
- Stream API for constant-memory processing of large log files

**License:** Apache 2.0  
**Language:** Java 17+  
**Author disclosure:** I am the author.
```

---

## 3. awesome-llm-security (or awesome-ai-security)

**Repo:** https://github.com/corca-ai/awesome-llm-security  
**Section to add under:** `Data Privacy` or `Input/Output Sanitization`  
**Line to insert:**

```markdown
- [Semantic Privacy Guard](https://github.com/Sushegaad/Semantic-Privacy-Guard) - Java PII firewall for LLM pipelines. Redacts sensitive data from prompts before they reach the model; de-tokenizes responses so end-users see real values. Spring AI native integration.
```

**PR Title:** `Add Semantic Privacy Guard – Java PII firewall for LLM pipelines`

**PR Body:**
```
## Add Semantic Privacy Guard

**Link:** https://github.com/Sushegaad/Semantic-Privacy-Guard

### Why LLM security is the core use case

SPG is designed as a "privacy firewall" that sits between an application and
its LLM API:

```
User prompt (with PII)
        │
        ▼
   SPG.redact()          → [EMAIL_1], [SSN_1] (PII removed)
        │
        ▼
   LLM API call          → model never sees real PII
        │
        ▼
   SPG de-tokenize()     → real values restored in the reply
        │
        ▼
   Response to user
```

**Features relevant to LLM security:**
- Spring AI `CallAroundAdvisor` — one annotation, automatic prompt redaction
- Spring Boot servlet filter — redacts API request/response bodies
- Reverse-map de-tokenization for round-trip safety
- 100% offline — prompts never leave the JVM for PII scanning
- 206,000+ sentences/sec — adds < 5ms latency to typical prompts
- Apache 2.0, Maven Central

**Author disclosure:** I am the author.
```

---

## Submission checklist

- [ ] Fork awesome-java, add the line, open PR with the body above
- [ ] Fork awesome-privacy, add the line, open PR with the body above
- [ ] Fork awesome-llm-security, add the line, open PR with the body above
- [ ] After any PR is merged, add the shield to README.md:
  `[![Awesome](https://awesome.re/mentioned-badge.svg)](link-to-list)`
