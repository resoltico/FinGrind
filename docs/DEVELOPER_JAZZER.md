---
afad: "4.0"
version: "0.50.0"
domain: DEVELOPER_JAZZER
updated: "2026-06-01"
route:
  keywords: [fingrind, jazzer, fuzzing, local-only, wrappers, regression, replay, sqlite, cli, reversal]
  questions: ["how is jazzer used in fingrind", "which fuzz targets does fingrind ship", "how do I run active fuzzing in fingrind", "what is the supported jazzer operator surface in fingrind"]
---

# Jazzer Developer Reference

**Purpose**: Explain FinGrind's nested Jazzer build, the supported operator surface, and the local-only fuzzing policy.
**Companion references**:
- [DEVELOPER.md](./DEVELOPER.md) for the root build, quality gates, and GitHub workflow stance.
- [DEVELOPER_GRADLE.md](./DEVELOPER_GRADLE.md) for the root-versus-nested build split and shared build logic.
- [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md) for command usage, local state, and cleanup.
- [DEVELOPER_JAZZER_COVERAGE.md](./DEVELOPER_JAZZER_COVERAGE.md) for the harness matrix and committed seed floor.

## Build Boundary

The Jazzer work lives in a dedicated nested Gradle build under `jazzer/`.
That separation is deliberate:
- root `./gradlew check` stays CI-friendly
- fuzzing-only dependencies stay isolated
- committed regression replay remains explicit
- the nested build imports the root version catalog and shared build logic instead of carrying its
  own parallel dependency authority
- the nested build compiles and injects its own managed SQLite 3.53.2 / SQLite3 Multiple Ciphers
  2.3.5 runtime from the same vendored source used by the root build
- GitHub workflows do not run active fuzzing; Jazzer remains local-only by design

FinGrind now has two distinct Jazzer operator surfaces of its own:
- deterministic local and CI-safe verification through the root-owned `jazzerCheck` Gradle task,
  exposed to operators as `jazzer/bin/check` and complemented by `jazzer/bin/test` and
  `jazzer/bin/regression`
- active local fuzzing through the remaining `jazzer/bin/*` wrappers

Active harness launching now goes through Jazzer's official command-line JUnit runner instead of a
local reimplementation of class discovery. That keeps the operator path aligned with Jazzer's real
`@FuzzTest` semantics.

## Supported Operator Surface

Use these surfaces intentionally:

- `jazzer/bin/test`
- `jazzer/bin/regression`
- `jazzer/bin/check`
- `./gradlew jazzerCheck`
- `jazzer/bin/clean-local-findings`
- `jazzer/bin/clean-local-corpus`

Those commands are the supported deterministic and local-hygiene Jazzer surface. The root-owned
`jazzerCheck` task is the authoritative deterministic verification owner; `jazzer/bin/check`
delegates to it, while the remaining wrappers keep their direct nested-build ownership for replay,
regression, and cleanup. Only the deterministic verification commands belong in GitHub workflows.

For active fuzzing, use only:

- `jazzer/bin/fuzz-cli-request`
- `jazzer/bin/fuzz-ledger-plan-request`
- `jazzer/bin/fuzz-posting-workflow`
- `jazzer/bin/fuzz-sqlite-book-roundtrip`
- `jazzer/bin/fuzz-all`
- `jazzer/bin/replay`
- `jazzer/bin/list-findings`
- `jazzer/bin/promote-seed`
- `jazzer/bin/seed-audit`

Do not run Jazzer workflows through raw `./gradlew -p jazzer ...` task invocations. Those nested
build tasks exist as wrapper internals, but they are not the supported Jazzer operator surface.

## Safety Model

The supported local wrapper surface under `jazzer/bin/*` exists to own the operational details
that raw Gradle does not communicate clearly enough on its own:

- active fuzzing is forced onto `--no-daemon`
- only one FinGrind verification command runs at a time through the repo-wide verification lock
- active runs write per-target `latest.log` plus timestamped history logs
- wrapper-owned interrupt handling tears down the launched Gradle client tree
- wrapper-owned duration watchdogs wait for libFuzzer startup, then enforce the requested max
  duration plus a fixed grace window while also guarding startup hangs with a separate startup
  ceiling
- the all-target wrapper stops immediately after an actionable harness failure and prints
  replay-classified findings before control returns to the shell, while wrapper-enforced timeout
  teardown remains distinct from ordinary bounded Jazzer completion
- replay-backed operator commands classify raw libFuzzer artifacts before humans or agents treat
  them as bugs
- active fuzzing preloads a tiny project-owned premain agent so Java 26 does not depend on late
  self-attach behavior
- wrapper scripts must remain compatible with stock macOS `/bin/bash` 3.2 under `set -u`, even
  when no optional Gradle arguments are passed

Active harness execution also hard-fails when `GITHUB_ACTIONS=true`.
That hard block is deliberate defense in depth: GitHub workflows already avoid active fuzzing, and
the harness runner rejects it again if a future workflow accidentally wires in a live fuzz task.

## Topology Contract

Harness metadata and runnable target ownership live in
`jazzer/src/main/resources/dev/erst/fingrind/jazzer/support/jazzer-topology.json`.
That file is consumed by:
- shared Gradle build logic for task registration
- runtime support classes `JazzerHarness` and `JazzerRunTarget`
- topology tests that assert stable ordering, task-name lookup, and one-harness-per-active-target invariants

When adding, renaming, or removing a harness, update the topology file and the matching fuzz/test
sources together.

Each active harness class must declare exactly one `@FuzzTest` method. The standalone harness
runner enforces that contract before it hands control to Jazzer, so do not add extra JUnit tests
or tag-based launcher hints to fuzz classes.

## Main Commands

```bash
./gradlew jazzerCheck --console=plain
jazzer/bin/test --console=plain
jazzer/bin/regression --console=plain
jazzer/bin/check --console=plain
jazzer/bin/fuzz-cli-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-ledger-plan-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-posting-workflow -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-all -PjazzerMaxDuration=30s --console=plain
jazzer/bin/replay cli-request jazzer/.local/runs/cli-request/crash-<sha1> --console=plain
jazzer/bin/list-findings cli-request --console=plain
jazzer/bin/seed-audit --console=plain
jazzer/bin/promote-seed cli-request jazzer/.local/runs/cli-request/crash-<sha1> --name seed_name --intent "coverage intent" --console=plain
jazzer/bin/clean-local-findings
jazzer/bin/clean-local-corpus
```

For committed seeds:
- `--name` must use lower_snake_case ASCII letters, digits, and underscores
- `--intent` must describe one committed behavior or invariant uniquely across the corpus
- `--json` returns structured success output on success and a structured error payload on
  deterministic operator failures, including wrapper-side target and input validation, without
  leaking Gradle task boilerplate
- `jazzer/bin/promote-seed --help` and `jazzer/bin/seed-audit --help` now print the supported
  replayable `<target-key>` values directly from the committed topology contract

The cleanup wrappers are intentionally best-effort around preserved corpus directories: they skip
corpus subtrees when clearing findings, and they emit warnings instead of aborting if the host
filesystem leaves one corpus root temporarily undeletable.

## Harness Inventory

| Harness | Focus | Current Assertions |
|:--------|:------|:-------------------|
| `cli-request` | raw JSON request decoding | valid requests parse, source channel is stamped `CLI`, forbidden committed-audit fields are rejected |
| `ledger-plan-request` | ledger-plan JSON request decoding | valid plans parse, `open-book` remains first when present, assertion steps keep their canonical kind, removed `executionPolicy` is rejected, oversize plans are rejected at 100 steps, and unknown kind typos do not fall through into assertion-shape errors |
| `posting-workflow` | application preflight and commit behavior | unopened books reject first, undeclared accounts reject next, inactive accounts reject after deactivation, accepted requests commit once after explicit setup, deterministic rejections repeat consistently, duplicates reject deterministically |
| `sqlite-book-roundtrip` | real filesystem persistence | unopened books reject, undeclared accounts reject, inactive accounts reject after direct deactivation, committed facts reload durably from one selected protected book using deterministic UTF-8 passphrase material, committed books and executed read/report commands render through the real CLI response writers, corrupt or directory-backed pre-schema paths map into owned runtime failures, concurrent contenders leave one durable winner plus one deterministic non-winning outcome, derived reversal near misses and duplicate reversals stay deterministic, the canonical Phase 2 schema stays `STRICT`, and open store connections keep the SQLite hardening pragmas |

## Deterministic Nested Tests

The nested Jazzer build also includes normal JUnit deterministic tests that cover:
- harness runner argument parsing and progress pulses
- explicit single-`@FuzzTest` harness discovery and failure shaping
- regression runner replay semantics
- direct replay classification for accepted and rejected seeds
- shared topology ordering and task-resolution contract
- committed-seed metadata completeness and path hygiene

The nested Jazzer build now also applies the shared `dev.erst.fingrind.java-conventions` plugin, so
Spotless, Error Prone, NullAway, PMD, JaCoCo, and the shared source/Jackson policy tasks gate the
replay engine, CLI tooling, wrapper-facing support code, and deterministic tests the same way they
already gate production modules.
The fuzz source set uses a dedicated PMD ruleset on top of that shared stack so single-method
`@FuzzTest` harnesses are linted as fuzz executables rather than misclassified as empty JUnit
test suites.

## Committed Seed Inventory

| Harness | Count | Coverage Shape |
|:--------|:------|:---------------|
| `cli-request` | `10` | valid parse, valid reversal parse, legacy correction rejection, exponent rejection, duplicate key rejection, missing provenance, unexpected field, forbidden recorded-at, forbidden source-channel, unbalanced entry |
| `ledger-plan-request` | `7` | valid plan execution, structured list-query journal facts, rejected missing-book list-query plans without fake row facts, removed execution-policy rejection, open-book ordering rejection, 100-step protocol-limit rejection, and unknown kind rejection without assertion fallthrough |
| `posting-workflow` | `5` | explicit lifecycle setup plus four-line success with optional correlation id, invalid actor, exponent rejection with reversal payload present, invalid missing reversal reason, missing reversal target |
| `sqlite-book-roundtrip` | `7` | explicit lifecycle setup plus success with distinct system provenance, nested path, invalid Unicode account-code rejection, exponent rejection with optional provenance correlation, invalid type, invalid missing reversal reason, missing reversal target; valid parsed seeds also drive executed read/report rendering, corrupt pre-schema path failures, concurrent contenders, and derived reversal near-miss coverage |

## Regression Philosophy

Regression metadata is committed on purpose.
It makes the currently expected replay result explicit and reviewable:
- each committed seed now carries one required coverage-intent string
- that coverage-intent string is unique across the committed corpus so one intent maps to one
  pinned behavior
- `jazzer/bin/promote-seed` is the project-owned path for adding that seed plus metadata together
- `jazzer/bin/seed-audit` is the project-owned proof that the committed floor has no duplicate raw
  inputs, no orphaned committed inputs, no metadata entries that encode `unexpected-failure`, no
  escaped or missing metadata input references, and no malformed committed `.json` seed bodies
- the deterministic Jazzer test floor keeps a second guard on committed `.json` seed syntax, so
  broken raw inputs fail before a later replay pass has to rediscover them indirectly
- processed Jazzer resource outputs are pruned before each real resource sync, so renaming or
  deleting one committed seed cannot leave an older packaged corpus entry behind in
  `jazzer-build/resources/`
- successful parses are treated as contract
- expected invalid requests are treated as stable contract, not as noise
- deterministic rejections are replayed as success-path contract outcomes
- raw local `crash-*` / `timeout-*` / `oom-*` / `leak-*` / `slow-unit-*` files stay disposable
  until `jazzer/bin/replay` or `jazzer/bin/list-findings` classifies them
- only replayed `unexpected-failure` findings should be treated as bugs
- `jazzer/bin/promote-seed` refuses replayed `unexpected-failure` findings until the bug is fixed
- replay-clean and expected-invalid raw artifacts should be cleaned before final verification

## Coverage Authority

This overview does not maintain a separate open-gap register.
The canonical coverage inventory and any future proven gaps belong in
`docs/DEVELOPER_JAZZER_COVERAGE.md`.
