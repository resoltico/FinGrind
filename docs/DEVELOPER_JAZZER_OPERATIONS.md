---
afad: "3.5"
version: "0.1.0"
domain: DEVELOPER_JAZZER_OPERATIONS
updated: "2026-04-09"
route:
  keywords: [fingrind, jazzer, operations, gradle-tasks, corpus, findings, regression, fuzzing, cleanup]
  questions: ["how do I run the fingrind fuzzers", "where does jazzer write corpus files in fingrind", "how do I clean local jazzer state in fingrind"]
---

# Jazzer Operations Reference

**Purpose**: Day-to-day runbook for the FinGrind Jazzer layer.
**Architecture reference**: [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
**Coverage inventory**: [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)

## Supported Tasks

| Task | Purpose |
|:-----|:--------|
| `./gradlew -p jazzer test` | run deterministic Jazzer support tests |
| `./gradlew -p jazzer jazzerRegression` | replay the committed seed floor |
| `./gradlew -p jazzer fuzzCliRequest` | fuzz raw request parsing |
| `./gradlew -p jazzer fuzzPostingWorkflow` | fuzz application write workflow |
| `./gradlew -p jazzer fuzzSqliteBookRoundTrip` | fuzz durable SQLite single-book round-trips |
| `./gradlew -p jazzer fuzzAllLocal` | run all three active fuzz tasks sequentially |
| `./gradlew -p jazzer cleanLocalFindings` | delete crash files and non-corpus run state |
| `./gradlew -p jazzer cleanLocalCorpus` | delete generated local corpora |
| `./gradlew -p jazzer check` | run support tests plus regression replay |

## Common Workflows

### Run the full local gate

```bash
./check.sh --console=plain
```

This runs root verification first and then nested `jazzer check`.

### Run the CI-equivalent Jazzer gate only

```bash
./gradlew -p jazzer test jazzerRegression --no-daemon
```

### Run one active harness

```bash
./gradlew -p jazzer fuzzSqliteBookRoundTrip -PjazzerMaxDuration=10s --no-daemon
```

Accepted throttles come directly from the nested build:
- `-PjazzerMaxDuration=<duration>`
- `-PjazzerMaxExecutions=<count>`

### Run all active harnesses sequentially

```bash
./gradlew -p jazzer fuzzAllLocal -PjazzerMaxDuration=10s --no-daemon
```

### Clean local state before a fresh fuzz pass

```bash
./gradlew -p jazzer cleanLocalFindings cleanLocalCorpus --no-daemon
```

## Output Model

FinGrind intentionally keeps the current local output model simple.

Each active harness uses:

```text
jazzer/.local/runs/<target>/
├── .cifuzz-corpus/
├── crash-*
├── leak-*
├── oom-*
├── slow-unit-*
└── timeout-*
```

What these artifacts mean:
- `.cifuzz-corpus/`: generated local corpus for that harness
- `crash-*`: replayable failing input captured by libFuzzer
- `timeout-*`: timeout-class finding captured by libFuzzer
- `oom-*`: out-of-memory finding captured by libFuzzer
- `leak-*`: leak-class finding captured by libFuzzer
- `slow-unit-*`: unusually slow input worth manual inspection even when the run succeeds

There is still no promotion or corpus-summary CLI. Today the primary operator surface is:
- committed seeds in source control
- committed regression metadata in source control
- the Gradle tasks above
- direct inspection of `.local/runs/`

## Progress Pulses

The nested build emits `[JAZZER-PULSE]` lines during support tests, regression replay, and active
fuzzing. Treat them as the canonical semantic progress markers.

Interpretation:
- `phase=plan total-tests=0` is normal for active fuzzing, because the standalone harness runner is
  still launching a Jazzer-backed JUnit test before the fuzz session body starts
- support tests now emit `class-start`, `test-complete`, `class-complete`, and throttled
  `test-progress` heartbeats so `./check.sh` can observe long-running support tests without false
  stalls
- regression replay now emits `regression-target phase=plan total-inputs=...`, one
  `regression-input ... completed=...` pulse per committed seed, and a final
  `regression-target phase=finish ...` pulse per harness
- later `phase=test-complete` lines confirm actual support-test or fuzz-session execution
- libFuzzer coverage and corpus-growth lines after the pulses are the active fuzzing body

## Operational Rules

- Keep active fuzzing local. CI runs regression only.
- Keep local corpora uncommitted.
- Treat any new `crash-*` file as a bug until replay or root-cause analysis proves otherwise.
- Clean findings after intentional fixes so the local run directory reflects the current state.
