# Contributing to Semantic Privacy Guard

Thank you for your interest in contributing! This project thrives on community involvement.

## Getting Started

1. Fork the repository and clone your fork.
2. Ensure you have JDK 17+ and Maven 3.8+ installed.
3. Run `mvn verify` to confirm a clean baseline build.

## Development Guidelines

- **Zero runtime dependencies** — do not add any `<scope>compile</scope>` dependencies to `pom.xml`. Test-scope dependencies are fine.
- **Thread safety** — all public classes must be safe for concurrent use.
- **Test coverage** — new code should maintain the ≥ 80% line coverage enforced by JaCoCo.
- **Javadoc** — all public methods require Javadoc. Run `mvn javadoc:javadoc` to validate.

## Areas Where Contributions Are Especially Welcome

- **ML training corpus** — adding labelled examples to `TrainingData.java` improves name/org disambiguation accuracy.
- **International PII patterns** — national ID patterns for non-US countries (UK NIN, Indian Aadhaar, EU VAT, etc.).
- **ReDoS safety** — if you find a pattern that exhibits catastrophic backtracking on adversarial input, please open an issue or a PR.
- **Benchmark data** — real-world throughput measurements on different hardware are always useful.

## Pull Request Process

1. Open an issue first for large changes so we can discuss approach.
2. Write tests that cover the new behaviour.
3. Run `mvn verify` and ensure the build is green.
4. Open a PR against `main` with a clear description.

## Code of Conduct

Be respectful and constructive. This project follows the [Contributor Covenant](https://www.contributor-covenant.org/).
