---
afad: "3.5"
version: "0.9.0"
domain: DEVELOPER_JAZZER
updated: "2026-04-14"
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
- fuzzing support dependencies stay isolated
- committed regression replay remains explicit
- the nested build imports the root version catalog and shared build logic instead of carrying its
  own parallel dependency authority
- the nested build compiles and injects its own managed SQLite 3.53.0 / SQLite3 Multiple Ciphers
  2.3.3 runtime from the same vendored source used by the root build
- GitHub workflows do not run active fuzzing; Jazzer remains local-only by design

FinGrind now has two distinct Jazzer entrypoint classes of its own:
- deterministic nested-build verification through `./gradlew -p jazzer ...`
- active local fuzzing through `jazzer/bin/*`

Active harness launching now goes through Jazzer's official command-line JUnit runner instead of a
local reimplementation of class discovery. That keeps the operator path aligned with Jazzer's real
`@FuzzTest` semantics.

## Supported Operator Surface

Use these surfaces intentionally:

- `./gradlew -p jazzer test`
- `./gradlew -p jazzer jazzerRegression`
- `./gradlew -p jazzer check`

Those Gradle commands are the deterministic nested-build surface. They are the supported way to
run Jazzer support tests and committed-seed replay locally, and they are the only Jazzer-shaped
surface that GitHub should ever exercise.

For active fuzzing, use only:

- `jazzer/bin/fuzz-cli-request`
- `jazzer/bin/fuzz-posting-workflow`
- `jazzer/bin/fuzz-sqlite-book-roundtrip`
- `jazzer/bin/fuzz-all`

Do not run active fuzzing through raw `./gradlew -p jazzer fuzz...` task invocations. Those tasks
exist as build internals under the wrapper scripts, but they are not the supported fuzz operator
surface.

## Safety Model

The supported local wrapper surface under `jazzer/bin/*` exists to own the operational details
that raw Gradle does not communicate clearly enough on its own:

- active fuzzing is forced onto `--no-daemon`
- only one Jazzer command runs at a time through `jazzer/.local/run-lock`
- active runs write per-target `latest.log` plus timestamped history logs
- wrapper-owned interrupt handling tears down the launched Gradle client tree
- wrapper-owned duration watchdogs enforce the requested max duration plus a fixed grace window
- active fuzzing preloads a tiny project-owned premain agent so Java 26 does not depend on late
  self-attach behavior

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
./gradlew -p jazzer test
./gradlew -p jazzer jazzerRegression
./gradlew -p jazzer check
jazzer/bin/regression --console=plain
jazzer/bin/fuzz-cli-request -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-posting-workflow -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-sqlite-book-roundtrip -PjazzerMaxDuration=30s --console=plain
jazzer/bin/fuzz-all -PjazzerMaxDuration=30s --console=plain
jazzer/bin/clean-local-findings
jazzer/bin/clean-local-corpus
```

## Harness Inventory

| Harness | Focus | Current Assertions |
|:--------|:------|:-------------------|
| `cli-request` | raw JSON request decoding | valid requests parse, source channel is stamped `CLI`, forbidden committed-audit fields are rejected |
| `posting-workflow` | application preflight and commit behavior | unopened books reject first, undeclared accounts reject next, inactive accounts reject after deactivation, accepted requests commit once after explicit setup, deterministic rejections repeat consistently, duplicates reject deterministically |
| `sqlite-book-roundtrip` | real filesystem persistence | unopened books reject, undeclared accounts reject, inactive accounts reject after direct deactivation, committed facts reload durably from one selected protected book using a temp UTF-8 key file, the canonical Phase 2 schema stays `STRICT`, and open store connections keep the SQLite hardening pragmas |

## Deterministic Support Tests

The nested Jazzer build also includes normal JUnit support tests that cover:
- harness runner argument parsing and progress pulses
- explicit single-`@FuzzTest` harness discovery and failure shaping
- regression runner replay semantics
- direct replay classification for accepted and rejected seeds
- shared topology ordering and task-resolution contract
- committed-seed metadata completeness and path hygiene

## Committed Seed Inventory

| Harness | Count | Coverage Shape |
|:--------|:------|:---------------|
| `cli-request` | `8` | valid parse, valid reversal parse, legacy correction rejection, exponent rejection, missing provenance, forbidden recorded-at, forbidden source-channel, unbalanced entry |
| `posting-workflow` | `5` | explicit lifecycle setup plus success, invalid actor, exponent rejection, missing reversal reason, missing reversal target |
| `sqlite-book-roundtrip` | `7` | explicit lifecycle setup plus success, nested path, Unicode round-trip, exponent rejection, invalid type, missing reversal reason, missing reversal target |

## Regression Philosophy

Regression metadata is committed on purpose.
It makes the currently expected replay result explicit and reviewable:
- successful parses are treated as contract
- expected invalid requests are treated as stable contract, not as noise
- deterministic rejections are replayed as success-path contract outcomes
- local fuzz findings stay disposable and must be cleaned before final verification

## Current Gaps

Not yet fuzzed:
- multi-command batch surfaces beyond one request at a time
- concurrent access between multiple writers
- corrupt or directory-backed pre-existing book paths before any valid schema exists
- CLI JSON response rendering
