---
afad: "3.5"
version: "0.2.0"
domain: DEVELOPER_JAZZER
updated: "2026-04-09"
route:
  keywords: [fingrind, jazzer, fuzzing, regression, replay, coverage, sqlite, cli, correction]
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

## Main Commands

```bash
./gradlew -p jazzer test
./gradlew -p jazzer jazzerRegression
./gradlew -p jazzer check
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
- regression runner replay semantics
- direct replay classification for accepted and rejected seeds
- committed-seed metadata completeness and path hygiene

## Committed Seed Inventory

| Harness | Count | Coverage Shape |
|:--------|:------|:---------------|
| `cli-request` | `6` | valid parse, correction parse, missing provenance, forbidden recorded-at, forbidden source-channel, unbalanced entry |
| `posting-workflow` | `4` | success, invalid actor, missing correction reason, missing correction target |
| `sqlite-book-roundtrip` | `5` | success, nested path, invalid type, missing correction reason, missing correction target |

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
- absence or corruption of the external `sqlite3` binary itself
- CLI JSON response rendering
