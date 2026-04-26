---
afad: "3.5"
version: "0.27.0"
domain: DEVELOPER_JAZZER_OPERATIONS
updated: "2026-04-26"
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
| `./gradlew -p jazzer test` | run deterministic Jazzer tests |
| `./gradlew -p jazzer jazzerRegression` | replay the committed seed floor |
| `./gradlew -p jazzer check` | run deterministic tests plus regression replay |
| `jazzer/bin/regression` | replay the committed seed floor through the supported wrapper surface |
| `jazzer/bin/fuzz-cli-request` | fuzz raw request parsing |
| `jazzer/bin/fuzz-ledger-plan-request` | fuzz ledger-plan request parsing |
| `jazzer/bin/fuzz-posting-workflow` | fuzz application write workflow |
| `jazzer/bin/fuzz-sqlite-book-roundtrip` | fuzz durable SQLite single-book round-trips |
| `jazzer/bin/fuzz-all` | run all active fuzz tasks sequentially |
| `jazzer/bin/replay` | replay one raw local input against one harness |
| `jazzer/bin/list-findings` | classify raw local finding artifacts through deterministic replay |
| `jazzer/bin/clean-local-findings` | delete raw finding artifacts and non-corpus run state |
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
- stays compatible with stock macOS `/bin/bash` 3.2 under `set -u`, including zero-argument
  cleanup invocations such as `jazzer/bin/clean-local-findings`

### Run all active harnesses sequentially

```bash
jazzer/bin/fuzz-all -PjazzerMaxDuration=10s --console=plain
```

`-PjazzerMaxDuration` still applies per harness, not across the whole campaign.
The all-target wrapper now derives its harness list from the `activeFuzzing` targets in
`jazzer-topology.json`, calls those per-harness wrapper scripts in topology order, prints
start/finish markers for each one, keeps going after plain timeout exits, but stops immediately
after an actionable harness failure. Before it returns non-zero, it also runs
`jazzer/bin/list-findings` for the failed target so the replay-classified finding summary is not
buried beneath later harness logs.

### Replay one raw local input

```bash
jazzer/bin/replay cli-request \
  jazzer/.local/runs/cli-request/crash-<sha1> \
  --console=plain
```

Add `--json` before any forwarded Gradle arguments when machine-readable output is more useful.

### Classify the current raw finding artifacts

```bash
jazzer/bin/list-findings --console=plain
jazzer/bin/list-findings cli-request --console=plain
```

This command replays each raw local finding artifact and reports whether it currently reproduces as:
- `unexpected-failure`
- `expected-invalid`
- `replay-clean`

`jazzer/bin/replay --json` emits one stable machine payload with:
- `harnessKey`
- `outcomeKind`
- `message`
- `details`

`details.type` distinguishes fully parsed inputs from unparsed request shapes such as
`CLI_REQUEST_UNPARSED` and `LEDGER_PLAN_REQUEST_UNPARSED`.

### Replay the committed regression floor

```bash
jazzer/bin/regression --console=plain
```

### Clean local state before a fresh fuzz pass

```bash
jazzer/bin/clean-local-findings
jazzer/bin/clean-local-corpus
```

If the host filesystem leaves one preserved corpus root unreadable or temporarily undeletable,
the cleanup commands now emit a warning and continue instead of aborting the whole wrapper run.

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
- `crash-*`, `timeout-*`, `oom-*`, `leak-*`, `slow-unit-*`: raw libFuzzer artifact files
  written under libFuzzer's own naming scheme. These prefixes are not final product bug
  classifications on their own.

The shared wrapper lock lives at `jazzer/.local/run-lock/`.

There is still no promotion or corpus-summary CLI. Today the primary operator surface is:
- committed seeds in source control
- committed regression metadata in source control
- the supported commands above
- direct inspection of `.local/runs/`
- replay-backed classification through `jazzer/bin/replay` and `jazzer/bin/list-findings`

## Progress Pulses

The nested build emits `[JAZZER-PULSE]` lines during deterministic tests, regression replay, and active
fuzzing. Treat them as the canonical semantic progress markers.

Interpretation:
- active fuzzing now emits `phase=plan total-tests=1 fuzz-test=...` and
  `phase=finish status=... fuzz-test=... exit-code=...`, because the standalone harness runner
  resolves one concrete `@FuzzTest` method before delegating to Jazzer's official JUnit runner
- deterministic tests now emit deterministic-tests `phase=class-start`, `phase=test-complete`,
  `phase=class-complete`, and throttled `phase=test-progress` heartbeats so `./check.sh` can observe
  long-running deterministic tests without false stalls
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
- Treat raw libFuzzer artifact filenames as unclassified until `jazzer/bin/replay` or
  `jazzer/bin/list-findings` has replayed them.
- Treat replayed `unexpected-failure` findings as bugs.
- Clean findings after intentional fixes so the local run directory reflects the current state.
- If wrapper shell logic or topology changes, rerun at least one live `jazzer/bin/fuzz-*` command
  and the zero-argument cleanup scripts on the real macOS operator surface before calling the
  work complete.
