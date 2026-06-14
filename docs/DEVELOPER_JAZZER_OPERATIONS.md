---
afad: "4.0"
version: "0.54.0"
domain: DEVELOPER_JAZZER_OPERATIONS
updated: "2026-06-14"
route:
  keywords: [fingrind, jazzer, operations, wrappers, corpus, findings, regression, fuzzing, cleanup, docker, devcontainer, repo-lock]
  questions: ["how do i run the fingrind fuzzers", "where does jazzer write corpus files in fingrind", "how do i clean local jazzer state in fingrind", "how do i run a fingrind fuzzing session through docker", "do jazzer wrappers auto-enter docker"]
---

# Jazzer Operations Reference

**Purpose**: Day-to-day operator runbook for the FinGrind Jazzer layer.
**Architecture reference**: [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
**Coverage inventory**: [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md)

## Supported Tasks

| Task | Purpose |
|:-----|:--------|
| `./gradlew jazzerCheck` | run deterministic Jazzer tests plus regression replay through the root build |
| `jazzer/bin/test` | run deterministic Jazzer tests |
| `jazzer/bin/regression` | replay the committed seed floor |
| `jazzer/bin/check` | operator wrapper for the root-owned `jazzerCheck` gate |
| `jazzer/bin/fuzz-cli-request` | fuzz raw request parsing |
| `jazzer/bin/fuzz-ledger-plan-request` | fuzz ledger-plan request parsing |
| `jazzer/bin/fuzz-posting-workflow` | fuzz application write workflow |
| `jazzer/bin/fuzz-sqlite-book-roundtrip` | fuzz durable SQLite single-book round-trips |
| `jazzer/bin/fuzz-all` | run all active fuzz tasks sequentially |
| `jazzer/bin/replay` | replay one raw local input against one harness |
| `jazzer/bin/list-findings` | classify raw local finding artifacts through deterministic replay |
| `jazzer/bin/promote-seed` | commit one ad hoc input into the deterministic seed floor |
| `jazzer/bin/seed-audit` | summarize committed seeds and report corpus-integrity defects across metadata and raw inputs |
| `jazzer/bin/clean-local-findings` | delete raw finding artifacts and non-corpus run state |
| `jazzer/bin/clean-local-corpus` | delete generated local corpora |

Use `jazzer/bin/*` for all Jazzer operations.
Do not run Jazzer workflows through raw `./gradlew -p jazzer ...` tasks.

These scripts are thin shell wrappers, not a separate argument parser. They forward Gradle
properties and options to the nested Jazzer build, so flags such as
`-PjazzerMaxDuration=5m` and `--console=plain` are expected here. The flip side is that `--help`
now returns the wrapper's own usage and exits before lock acquisition or Gradle startup.

All supported Jazzer scripts now share the same repo-wide verification lock as `./check.sh`,
`./scripts/docker-smoke.sh`, and `./scripts/validate-devcontainer.sh`. Only one FinGrind
verification command should run at a time.

## Choose The Surface

- `./check.sh`: supported whole-repo local gate. Runs root verification first, then `jazzer/bin/check`,
  then packaging and Docker smoke.
- `jazzer/bin/test`, `jazzer/bin/regression`, and `jazzer/bin/check`: deterministic Jazzer
  verification entrypoints. Safe for GitHub Actions because they do not start active fuzzing, and
  they participate in the same repo-wide verification lock contract as `./check.sh`. The
  authoritative deterministic gate is `./gradlew jazzerCheck`; `jazzer/bin/check` is the supported
  wrapper over that root-owned task, while `jazzer/bin/test` and `jazzer/bin/regression` continue
  to target the nested build directly. Each deterministic entrypoint starts from a clean relocated
  nested-build output so removed classfiles cannot linger and poison coverage verification.
- `jazzer/bin/replay`, `jazzer/bin/list-findings`, `jazzer/bin/seed-audit`,
  `jazzer/bin/clean-local-findings`, and `jazzer/bin/clean-local-corpus`: read-only or
  maintenance wrapper surfaces. They use the same repo-wide verification lock, but they do not
  run a nested Gradle `clean` first because replay/classification, seed inspection, and local
  Jazzer cleanup must not wipe unrelated build outputs.
- `jazzer/bin/promote-seed`: the project-owned write path for turning one ad hoc raw input into a
  committed seed plus deterministic replay metadata. It uses the same repo-wide verification lock
  and rejects duplicate raw seed bytes across the committed corpus.
- `jazzer/bin/*`: the one supported Jazzer operator surface for active fuzzing, regression, replay,
  and cleanup. Active fuzz through this surface forces `--no-daemon` and owns interrupt and
  timeout teardown.
- that same wrapper surface must remain compatible with stock macOS `/bin/bash` 3.2 under
  `set -u`, including zero-argument cleanup scripts such as `jazzer/bin/clean-local-findings`
- wrapper arguments are forwarded through to Gradle tasks under `jazzer/`, so think of this
  surface as "project-owned launcher plus Gradle arguments", not as a bespoke standalone CLI
- wrapper target discovery is contract-owned by the committed
  `jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-topology.json` document;
  nested Gradle query tasks, the shell topology reader, and Java runtime support all project from
  that one file in canonical order

Do not run Jazzer workflows through raw `./gradlew -p jazzer ...` tasks. Those task names are an
implementation detail under the wrapper, not a supported operator interface.

Live fuzzing is local-only. Active harness execution hard-fails when `GITHUB_ACTIONS=true`.

## Common Workflows

### Run The Full Local Gate

```bash
./check.sh --console=plain
```

This runs root verification first and then `jazzer/bin/check`.

### Run The CI-Equivalent Jazzer Gate Only

```bash
./gradlew jazzerCheck --console=plain
```

or, through the supported wrapper:

```bash
jazzer/bin/check --no-daemon --console=plain
```

### Run One Active Harness

```bash
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=10s --console=plain
```

Accepted throttles still come directly from the nested build:

- `-PjazzerMaxDuration=<duration>`
- `-PjazzerMaxExecutions=<count>`

The wrapper adds the operator safety contract:

- forces `--no-daemon` for active fuzzing
- serializes all FinGrind verification commands through the shared repo lock
- writes `latest.log` and `history/<timestamp>/run.log`
- owns `INT` and `TERM` cleanup for the launched Gradle client tree
- stays compatible with stock macOS `/bin/bash` 3.2 under `set -u`, including zero-argument
  cleanup invocations such as `jazzer/bin/clean-local-findings`

### Run All Active Harnesses Sequentially

```bash
jazzer/bin/fuzz-all -PjazzerMaxDuration=10s --console=plain
```

`-PjazzerMaxDuration` still applies per harness, not across the whole campaign.
The all-target wrapper derives its harness list from the `activeFuzzing` targets in
`jazzer-topology.json`, calls those per-harness wrapper scripts in topology order, prints
start/finish markers for each one, and stops immediately after an actionable harness failure.
Bounded Jazzer completion exits successfully; wrapper exit `124` is reserved for timeout teardown
only when a harness misses its stop window after libFuzzer has started or never reaches the
libFuzzer start marker at all. Before the all-target wrapper returns non-zero, it also runs
`jazzer/bin/list-findings` for the failed target so the replay-classified finding summary is not
buried beneath later harness logs.

### Run One Docker-Only Fuzz Session From A Fresh Terminal

Use this when the repository is on your Mac, Docker Desktop is running, and you do not want to
install host Java, Gradle, or Jazzer tooling.

Important boundary:

- the repo stays on the host filesystem
- the container provides the Java and Gradle environment
- `jazzer/bin/*` does not auto-enter Docker on your behalf
- you enter the container first, then run the Jazzer commands there

Run these commands in order.

1. Change into the repository on the host:

   ```bash
   cd /absolute/path/to/FinGrind
   ```

   This is the directory Docker will bind-mount into the contributor container.

2. Confirm Docker Desktop is running:

   ```bash
   docker info >/dev/null && echo "Docker is running"
   ```

   If this command fails, start Docker Desktop first. Do not continue until it succeeds.

3. Build the contributor image from the committed devcontainer definition:

   ```bash
   docker build --pull -f .devcontainer/Dockerfile -t fingrind-fuzz-dev:local .devcontainer
   ```

   This image contains the contributor shell tools plus the pinned Azul Zulu 26 JDK.

4. Start an interactive shell inside that image:

   ```bash
   docker run --rm -it \
     --name fingrind-fuzz-shell \
     -e HOME=/tmp/fingrind-home \
     -e GRADLE_USER_HOME=/tmp/fingrind-home/.gradle \
     -v "$PWD":/workspaces/fingrind \
     -w /workspaces/fingrind \
     fingrind-fuzz-dev:local \
     bash
   ```

   Why the temporary `HOME` and `GRADLE_USER_HOME` matter:

   - a plain `docker run` does not apply the full devcontainer runtime contract
   - named volumes mounted directly onto `/home/vscode/.gradle` or `/home/vscode/.cache` can come
     up owned by `root`
   - that ownership mismatch makes the Gradle wrapper fail before Jazzer starts
   - the temporary writable home avoids that trap for ad hoc terminal-only sessions

5. Now that you are inside the container, create the writable temporary directories:

   ```bash
   mkdir -p "$HOME" "$GRADLE_USER_HOME"
   ```

6. Prove the container has the expected Java toolchain:

   ```bash
   java --version
   javac --version
   ```

   Expected result:

   - both commands succeed
   - Java major version is `26`
   - the vendor string shows Azul Zulu

7. Start one short real fuzz session:

   ```bash
   ./jazzer/bin/fuzz-cli-request -PjazzerMaxDuration=15s --console=plain
   ```

   Why this is the recommended first session:

   - it exercises one real active harness
   - `15s` is short enough to prove the setup without committing to a long run
   - it shows the exact supported `jazzer/bin/*` invocation shape

   First-run truth boundary:

   - a completely fresh temporary `GRADLE_USER_HOME` can spend noticeable time downloading Gradle,
     compiling nested build logic, and compiling Jazzer classes before libFuzzer starts
   - if that very first short timebox expires during bootstrap, rerun the same command once in the
     same container shell before treating it as a wrapper failure
   - the second run in the same shell uses the already prepared Gradle home and is the right time
     to expect live libFuzzer `pulse` output quickly

8. Watch for active-fuzz output.

   A healthy active run is not silent. Expect lines such as:

   - `[JAZZER-PULSE] ... phase=plan ...`
   - `INFO: using inputs from: ...`
   - `#16384 pulse ...`
   - `#32768 pulse ...`
   - `#156789 DONE ...`
   - `[JAZZER-PULSE] ... phase=finish status=SUCCESS ...`
   - `BUILD SUCCESSFUL`

   If the command returns immediately with no `JAZZER-PULSE`, no `INFO:`, and no libFuzzer
   `pulse` lines, treat that as a failed launch and investigate the wrapper or container setup.

9. Inspect the most recent run log:

   ```bash
   tail -n 40 jazzer/.local/runs/cli-request/latest.log
   ```

10. If the run produced raw finding artifacts, classify them:

    ```bash
    ./jazzer/bin/list-findings cli-request --console=plain
    ```

11. When you are done, leave the container:

    ```bash
    exit
    ```

After you exit:

- the container process is removed because `docker run` used `--rm`
- the repository changes and Jazzer artifacts remain on the host because the checkout was
  bind-mounted
- local run history lives under `jazzer/.local/runs/cli-request/`

If you later want to fuzz all four active harnesses instead of just one, use:

```bash
./jazzer/bin/fuzz-all -PjazzerMaxDuration=5m --console=plain
```

Run that from inside the container shell too. The `5m` duration applies per harness, not total, so
expect substantially longer than five minutes before setup overhead.

### Replay One Raw Local Input

```bash
jazzer/bin/replay cli-request \
  jazzer/.local/runs/cli-request/crash-<sha1> \
  --console=plain
```

Add `--json` before any forwarded Gradle arguments when machine-readable output is more useful.
The wrapper then suppresses Gradle task chatter so the emitted payload stays machine-clean JSON.
The wrapper validates that `<input-path>` resolves to one existing regular file before it starts
Gradle work, and a held repo-verification lock reports only the lock conflict instead of
mislabeling the chosen target as unknown.

### Classify The Current Raw Finding Artifacts

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

### Audit The Committed Seed Floor

```bash
jazzer/bin/seed-audit --console=plain
jazzer/bin/seed-audit --json --console=plain
jazzer/bin/seed-audit posting-workflow --console=plain
```

This command reports:

- committed seed counts per harness
- committed seed outcome kinds per harness
- the declared coverage intent for each committed seed
- orphaned committed raw inputs with no metadata entry
- committed metadata entries that encode `unexpected-failure`
- committed metadata that cannot be read, points outside its owning harness directory, or points to
  a missing/non-file raw input
- malformed committed `.json` raw seed bodies
- duplicate raw-input content groups

The command fails whenever any of those defects are present.

Use `--json` when a machine or agent needs the exact committed-seed inventory.
Deterministic `--json` failures on the seed-management commands are machine-readable too; they
return one error payload with `status`, `command`, `exitCode`, `message`, and `usage`.
Wrapper-side target validation also includes `supportedTargetKeys`, so a machine caller can
recover without opening docs or source.

### Promote One Ad Hoc Input Into A Committed Seed

```bash
jazzer/bin/promote-seed posting-workflow \
  jazzer/.local/runs/posting-workflow/crash-<sha1> \
  --name posted_reversal_exponent \
  --intent "exponent rejection with reversal payload present" \
  --console=plain
```

This command:

- replays the selected input through the chosen harness
- copies the raw input into the canonical committed input directory
- writes deterministic regression metadata with the observed replay expectation
- records one required `coverageIntent`
- requires that `coverageIntent` remain unique across the committed corpus
- requires `--name` to use lower_snake_case ASCII letters, digits, and underscores
- rejects duplicate raw input bytes across the committed corpus
- refuses `unexpected-failure` replay outcomes so active bugs cannot be codified into the committed floor

When `--json` is selected, deterministic operator failures also return the same structured error
payload instead of leaking Gradle task failure boilerplate.
Both `jazzer/bin/promote-seed --help` and `jazzer/bin/seed-audit --help` print the supported
replayable `<target-key>` values directly, so the write and audit surfaces remain black-box
discoverable.

### Replay The Committed Regression Floor

```bash
jazzer/bin/regression --console=plain
```

`jazzer/bin/regression` accepts Gradle-style options only. Positional arguments such as target keys
are rejected at the wrapper edge instead of falling through into raw Gradle task-name errors.

### Clean Local State Before A Fresh Fuzz Pass

```bash
jazzer/bin/clean-local-findings
jazzer/bin/clean-local-corpus
```

If the host filesystem leaves one preserved corpus root unreadable or temporarily undeletable,
the cleanup commands emit a warning and continue instead of aborting the whole wrapper run.

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
- `history/<timestamp>/timed-out`: wrapper-written marker when wrapper-enforced timeout teardown
  was required, either because fuzz startup never reached the libFuzzer start marker before the
  startup ceiling or because a started harness ran past the requested duration plus grace
- `crash-*`, `timeout-*`, `oom-*`, `leak-*`, `slow-unit-*`: raw libFuzzer artifact files written
  under libFuzzer's own naming scheme. These prefixes are not final product bug classifications on
  their own.

The local run-history root remains `jazzer/.local/runs/`, but lock ownership now lives in the
repo-wide verification lock under the user cache root
(`${XDG_CACHE_HOME:-$HOME/.cache}/fingrind/repo-verification-locks` by default, keyed by
repository path). Wrapper and nested Gradle reentry use the published lock-owner metadata from
that directory rather than shell-parent heuristics, so the supported monitored shell surfaces keep
working under `./check.sh`.

The seed operator surface is now explicit:

- committed seeds in source control
- committed regression metadata in source control
- `jazzer/bin/promote-seed`
- `jazzer/bin/seed-audit`
- direct inspection of `.local/runs/`
- replay-backed classification through `jazzer/bin/replay` and `jazzer/bin/list-findings`

## Progress Pulses

The nested build emits `[JAZZER-PULSE]` lines during deterministic tests, regression replay, and
active fuzzing. Treat them as the canonical semantic progress markers.

Interpretation:

- active fuzzing emits `phase=plan total-tests=1 fuzz-test=...` and
  `phase=finish status=... fuzz-test=... exit-code=...`, because the standalone harness runner
  resolves one concrete `@FuzzTest` method before delegating to Jazzer's official JUnit runner
- deterministic tests emit `deterministic-tests phase=class-start`, `phase=test-complete`,
  `phase=class-complete`, and throttled `phase=test-progress` heartbeats so `./check.sh` can
  observe long-running deterministic tests without false stalls
- regression replay emits `regression-target phase=plan total-inputs=...`, one
  `regression-input ... completed=...` pulse per committed seed, and a final
  `regression-target phase=finish ...` pulse per harness
- active fuzzing does not need per-seed launcher pulses; libFuzzer coverage and corpus-growth
  lines remain the fuzz-session body

## Operational Rules

- Keep active fuzzing local. GitHub CI does not run nested Jazzer active harness tasks.
- GitHub Actions must never run `jazzer/bin/*`; active harness execution hard-fails when
  `GITHUB_ACTIONS=true`.
- Treat raw `./gradlew -p jazzer fuzz...` task names as implementation details under the wrapper
  scripts.
- Keep local corpora uncommitted.
- Treat raw libFuzzer artifact filenames as unclassified until `jazzer/bin/replay` or
  `jazzer/bin/list-findings` has replayed them.
- Treat replayed `unexpected-failure` findings as bugs.
- Clean findings after intentional fixes so the local run directory reflects the current state.
- If wrapper shell logic or topology changes, rerun at least one live `jazzer/bin/fuzz-*` command
  and the zero-argument cleanup scripts on the real macOS operator surface before calling the work
  complete.
