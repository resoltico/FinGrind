---
afad: "3.5"
version: "0.5.0"
domain: DEVELOPER_JAZZER_OPERATIONS
updated: "2026-04-11"
route:
  keywords: [fingrind, jazzer, operations, wrappers, corpus, findings, regression, fuzzing, cleanup, run-lock]
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
| `./gradlew -p jazzer check` | run support tests plus regression replay |
| `jazzer/bin/regression` | replay the committed seed floor through the supported wrapper surface |
| `jazzer/bin/fuzz-cli-request` | fuzz raw request parsing |
| `jazzer/bin/fuzz-posting-workflow` | fuzz application write workflow |
| `jazzer/bin/fuzz-sqlite-book-roundtrip` | fuzz durable SQLite single-book round-trips |
| `jazzer/bin/fuzz-all` | run all three active fuzz tasks sequentially |
| `jazzer/bin/clean-local-findings` | delete crash files and non-corpus run state |
| `jazzer/bin/clean-local-corpus` | delete generated local corpora |

Use the Gradle entries above only for deterministic nested-build verification.
Use `jazzer/bin/*` for active fuzzing and local Jazzer operations.
Do not run active fuzzing through raw `./gradlew -p jazzer fuzz...` tasks.

## Common Workflows

### Run the full local gate

```bash
./check.sh --console=plain
```

This runs root verification first and then nested `jazzer check`.

### Run the CI-equivalent Jazzer gate only

```bash
./gradlew -p jazzer check --no-daemon --console=plain
```

### Run one active harness

```bash
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=10s --console=plain
```

Accepted throttles still come directly from the nested build:
- `-PjazzerMaxDuration=<duration>`
- `-PjazzerMaxExecutions=<count>`

The wrapper adds the operator safety contract:
- forces `--no-daemon` for active fuzzing
- serializes all Jazzer commands through `jazzer/.local/run-lock`
- writes `latest.log` and `history/<timestamp>/run.log`
- owns `INT` and `TERM` cleanup for the launched Gradle client tree

### Run all active harnesses sequentially

```bash
jazzer/bin/fuzz-all -PjazzerMaxDuration=10s --console=plain
```

### Replay the committed regression floor

```bash
jazzer/bin/regression --console=plain
```

### Clean local state before a fresh fuzz pass

```bash
jazzer/bin/clean-local-findings
jazzer/bin/clean-local-corpus
```

## Output Model

FinGrind intentionally keeps the current local output model simple and file-based.

Each active harness uses:

```text
jazzer/.local/runs/<target>/
├── .cifuzz-corpus/
├── latest.log
├── history/
│   └── <timestamp>/
│       ├── run.log
│       └── timed-out
├── crash-*
├── leak-*
├── oom-*
├── slow-unit-*
└── timeout-*
```

What these artifacts mean:
- `.cifuzz-corpus/`: generated local corpus for that harness
- `latest.log`: log of the most recent run for that harness
- `history/<timestamp>/run.log`: immutable log for one completed or interrupted run
- `history/<timestamp>/timed-out`: wrapper-written marker when the requested duration plus grace was exceeded
- `crash-*`: replayable failing input captured by libFuzzer
- `timeout-*`: timeout-class finding captured by libFuzzer
- `oom-*`: out-of-memory finding captured by libFuzzer
- `leak-*`: leak-class finding captured by libFuzzer
- `slow-unit-*`: unusually slow input worth manual inspection even when the run succeeds

The shared wrapper lock lives at `jazzer/.local/run-lock/`.

There is still no promotion or corpus-summary CLI. Today the primary operator surface is:
- committed seeds in source control
- committed regression metadata in source control
- the supported commands above
- direct inspection of `.local/runs/`

## Progress Pulses

The nested build emits `[JAZZER-PULSE]` lines during support tests, regression replay, and active
fuzzing. Treat them as the canonical semantic progress markers.

Interpretation:
- active fuzzing now emits `phase=plan total-tests=1 fuzz-test=...` and
  `phase=finish status=... fuzz-test=... exit-code=...`, because the standalone harness runner
  resolves one concrete `@FuzzTest` method before delegating to Jazzer's official JUnit runner
- support tests now emit `class-start`, `test-complete`, `class-complete`, and throttled
  `test-progress` heartbeats so `./check.sh` can observe long-running support tests without false
  stalls
- regression replay now emits `regression-target phase=plan total-inputs=...`, one
  `regression-input ... completed=...` pulse per committed seed, and a final
  `regression-target phase=finish ...` pulse per harness
- active fuzzing does not need per-seed launcher pulses anymore; libFuzzer coverage and
  corpus-growth lines remain the fuzz-session body

## Operational Rules

- Keep active fuzzing local. GitHub CI does not run nested Jazzer tasks today.
- GitHub Actions must never run `jazzer/bin/*`; active harness execution hard-fails when `GITHUB_ACTIONS=true`.
- Treat raw `./gradlew -p jazzer fuzz...` task names as implementation details under the wrapper scripts.
- Keep local corpora uncommitted.
- Treat any new `crash-*` file as a bug until replay or root-cause analysis proves otherwise.
- Clean findings after intentional fixes so the local run directory reflects the current state.
