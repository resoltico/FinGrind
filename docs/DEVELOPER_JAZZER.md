---
afad: "3.5"
version: "0.1.0"
domain: DEVELOPER_JAZZER
updated: "2026-04-09"
route:
  keywords: [fingrind, jazzer, fuzzing, nested-build, regression, corpus, local-only, composite-build, sqlite-book]
  questions: ["how is jazzer wired into fingrind", "what does the fingrind jazzer layer cover", "why is jazzer a nested build in fingrind"]
---

# Jazzer Developer Reference

**Purpose**: Architecture and policy reference for the FinGrind Jazzer layer.
**Companion references**:
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)
- [DEVELOPER.md](./DEVELOPER.md)

## Status

As of `2026-04-09`, the Jazzer layer is implemented and working end to end.

Implemented now:
- standalone nested Gradle build at `jazzer/`
- composite-build linkage back to the root project through `includeBuild("..")`
- deterministic nested-build support tests under `jazzer/src/test/java/`
- three active fuzz harnesses plus a regression replay aggregate
- committed seed floor under `jazzer/src/fuzz/resources/`
- committed regression metadata under `jazzer/src/fuzz/resources/dev/erst/fingrind/jazzer/regression-metadata/`
- local corpora and finding artifacts under `jazzer/.local/runs/`

Deliberately not implemented:
- root-project dependency on active fuzzing
- root subproject integration for `jazzer/`
- wrapper scripts under `jazzer/bin/`
- promotion, replay-report, or corpus-summary tooling
- committed local corpus files
- committed dictionary files
- GitHub CI Jazzer replay
- CI active fuzzing

If implementation and this document diverge, fix one or the other immediately.

## Core Decisions

FinGrind uses Jazzer as a separate nested build, not as a root subproject.

The non-negotiable decisions are:
- `jazzer/` remains a nested local verification build.
- Root `./gradlew check` stays independent of active fuzzing.
- Root `./check.sh` runs nested `jazzer check` sequentially after the root build gates.
- The nested Jazzer build follows the same Java 26 shell contract as the root project.
- The standalone harness runner selects concrete harness classes directly, so the production fuzz
  harness classes are part of the public JVM surface inside the nested build.
- Generated state stays under `jazzer/.local/`.
- Only committed seed inputs belong in versioned source.
- Every committed regression seed also carries committed regression metadata.
- Regression replay runs through the direct replay engine, not through standalone JUnit
  `@FuzzTest` discovery.
- Active fuzzing targets the current hard-break single-book model.

The ordinary JUnit suite remains the primary correctness contract. Jazzer extends bug discovery
and input exploration; it does not replace standard tests.

## Harness Set

Current harnesses:
- `CliRequestFuzzTest`: raw request parsing and validation from JSON bytes
- `PostingWorkflowFuzzTest`: application write workflow over an in-memory book
- `SqliteBookRoundTripFuzzTest`: durable single-book round-trip against a real SQLite file path

The committed seed floor is intentionally small and every input has one committed replay contract:
- `cli-request`: 4 seeds
- `posting-workflow`: 3 seeds
- `sqlite-book-roundtrip`: 3 seeds
- total committed seeds: 10

## Findings Loop

The first active fuzz cycle already paid for itself. The request parser previously allowed several
malformed-input failures to escape as runtime exceptions. Active fuzzing surfaced and we fixed:
- malformed JSON syntax escaping Jackson runtime exceptions
- malformed date/time strings escaping `DateTimeParseException`
- invalid byte-encoding payloads escaping Jackson runtime exceptions

The current contract is that malformed request bytes are normalized into deterministic
invalid-request failures before they can look like engine or adapter bugs.

## Scope Lock

In scope:
- JSON request parsing from raw bytes
- application preflight and commit behavior
- duplicate-idempotency handling
- one-book-per-file SQLite persistence
- arbitrary filesystem paths for the selected book file

Out of scope:
- cross-book orchestration
- multi-process write coordination tests
- damaged SQLite file recovery
- country-pack schema behavior
- non-SQLite durable backends

## Repository Topology

```text
FinGrind/
├── docs/
│   ├── DEVELOPER.md
│   ├── DEVELOPER_JAZZER.md
│   ├── DEVELOPER_JAZZER_OPERATIONS.md
│   └── DEVELOPER_JAZZER_COVERAGE.md
├── jazzer/
│   ├── settings.gradle.kts
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── src/
│   │   ├── main/java/dev/erst/fingrind/jazzer/
│   │   ├── test/java/dev/erst/fingrind/jazzer/
│   │   └── fuzz/
│   │       ├── java/dev/erst/fingrind/cli/
│   │       └── resources/
│   │           ├── dev/erst/fingrind/cli/
│   │           └── dev/erst/fingrind/jazzer/regression-metadata/
│   └── .local/
│       └── runs/
│           ├── cli-request/
│           ├── posting-workflow/
│           └── sqlite-book-roundtrip/
└── ...
```

Tree rules:
- `jazzer/` is the only top-level home for Jazzer code and local fuzz state.
- committed seeds live in `jazzer/src/fuzz/resources/`
- committed regression metadata also lives in `jazzer/src/fuzz/resources/`
- generated corpora and crash artifacts live in `jazzer/.local/`
- root `docs/` carries the operator and contributor documentation for the nested build
