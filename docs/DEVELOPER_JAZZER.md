---
afad: "3.5"
version: "0.4.0"
domain: DEVELOPER_JAZZER
updated: "2026-04-10"
route:
  keywords: [fingrind, jazzer, fuzzing, regression, replay, coverage, sqlite, cli, reversal]
  questions: ["how is jazzer used in fingrind", "which fuzz targets does fingrind ship", "how do I run the fingrind jazzer checks"]
---

# Jazzer Developer Reference

**Purpose**: Explain the nested Jazzer build, supported harnesses, and committed regression workflow.
**Prerequisites**: Read [DEVELOPER.md](./DEVELOPER.md) for the root build, and use the nested `jazzer/` build only for fuzzing and regression replay.

## Build Boundary

The Jazzer work lives in a dedicated nested Gradle build under `jazzer/`.
That separation is deliberate:
- root `./gradlew check` stays CI-friendly
- fuzzing support dependencies stay isolated
- committed regression replay remains explicit
- the nested build imports the root version catalog and shared build logic instead of carrying its
  own parallel dependency authority
- the nested build compiles and injects its own managed SQLite 3.53.0 runtime from the same
  vendored source used by the root build
- GitHub workflows do not run active fuzzing; Jazzer remains local-only by design

Active harness launching now goes through Jazzer's official command-line JUnit runner instead of a
local reimplementation of class discovery. That keeps the operator path aligned with Jazzer's real
`@FuzzTest` semantics.

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
./gradlew -p jazzer cleanLocalFindings cleanLocalCorpus fuzzAllLocal -PjazzerMaxDuration=30s
./gradlew -p jazzer fuzzCliRequest -PjazzerMaxDuration=30s
./gradlew -p jazzer fuzzPostingWorkflow -PjazzerMaxDuration=30s
./gradlew -p jazzer fuzzSqliteBookRoundTrip -PjazzerMaxDuration=30s
./gradlew -p jazzer cleanLocalFindings cleanLocalCorpus
```

## Harness Inventory

| Harness | Focus | Current Assertions |
|:--------|:------|:-------------------|
| `cli-request` | raw JSON request decoding | valid requests parse, source channel is stamped `CLI`, forbidden committed-audit fields are rejected |
| `posting-workflow` | application preflight and commit behavior | accepted requests commit once, deterministic rejections repeat consistently, duplicates reject deterministically |
| `sqlite-book-roundtrip` | real filesystem persistence | committed facts reload durably from one selected book, deterministic rejections do not persist state |

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
| `posting-workflow` | `5` | success, invalid actor, exponent rejection, missing reversal reason, missing reversal target |
| `sqlite-book-roundtrip` | `6` | success, nested path, exponent rejection, invalid type, missing reversal reason, missing reversal target |

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
