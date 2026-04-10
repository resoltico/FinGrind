---
afad: "3.5"
version: "0.2.0"
domain: DEVELOPER_JAZZER_COVERAGE
updated: "2026-04-09"
route:
  keywords: [fingrind, jazzer, coverage, harness, replay, committed-seeds, sqlite, cli, rejection]
  questions: ["what does the fingrind jazzer suite currently cover", "which committed seeds exist for fingrind fuzzing", "what is still not covered by the jazzer suite"]
---

# Jazzer Coverage Snapshot

**Purpose**: Summarize what the committed Jazzer suite covers right now.

## Harness Coverage

| Harness | Main Surface | What It Proves | Seed Count |
|:--------|:-------------|:---------------|:-----------|
| `cli-request` | `CliRequestReader.readPostEntryCommand(...)` | request parsing, CLI source stamping, and forbidden committed-audit-field rejection | `6` |
| `posting-workflow` | `PostingApplicationService.preflight(...)` and `commit(...)` | application write contract, deterministic correction rejections, and duplicate-idempotency behavior | `4` |
| `sqlite-book-roundtrip` | `SqlitePostingFactStore` plus CLI request decoding | durable round-trip in one real SQLite book file and no-persist deterministic rejections | `5` |

## `cli-request`

Surface:
- raw JSON bytes
- request decoding through the real CLI reader
- domain-model construction for journal lines, entries, correction linkage, and request provenance

What it asserts:
- fresh valid requests parse successfully
- malformed JSON is normalized into `invalid-request`
- invalid domain shapes fail deterministically
- caller-supplied `sourceChannel` is not trusted; parsed commands always carry `CLI`
- caller-supplied `recordedAt` and `sourceChannel` are rejected because they are committed fields, not request fields

## `posting-workflow`

Surface:
- `PostingApplicationService.preflight(...)`
- `PostingApplicationService.commit(...)`
- correction admission policy
- commit through the in-memory runtime seam

What it asserts:
- fresh valid requests preflight successfully
- a first commit persists one `PostingFact`
- a duplicate commit returns `duplicate-idempotency-key`
- missing correction reason and missing correction target reject deterministically on both preflight and commit
- stored fact shape matches the parsed command when commit succeeds

## `sqlite-book-roundtrip`

Surface:
- request parsing through the same CLI seam
- `SqlitePostingFactStore` canonical-schema initialization on commit
- commit and reload against a real filesystem path
- reopening the same SQLite book file in a fresh adapter instance

What it asserts:
- one valid request commits durably into one selected book file
- reloading by idempotency returns the same fact shape
- duplicate commit attempts are rejected in the same book
- parent-directory creation works for nested arbitrary paths
- deterministic correction rejections do not create or mutate durable book state

## Committed Seed Inventory

| Harness | Input | Meaning |
|:--------|:------|:--------|
| `cli-request` | `basic_valid.json` | minimal valid posting request |
| `cli-request` | `correction_valid.json` | valid request carrying correction linkage |
| `cli-request` | `invalid_forbidden_recorded_at.json` | rejected committed-audit request field |
| `cli-request` | `invalid_forbidden_source_channel.json` | rejected committed-audit request field, even as `null` |
| `cli-request` | `invalid_missing_provenance.json` | missing provenance object |
| `cli-request` | `invalid_unbalanced.json` | unbalanced journal entry |
| `posting-workflow` | `basic_valid.json` | successful preflight then commit |
| `posting-workflow` | `correction_reason_required.json` | deterministic rejection for missing correction reason |
| `posting-workflow` | `correction_target_missing.json` | deterministic rejection for missing correction target |
| `posting-workflow` | `invalid_blank_actor.json` | invalid provenance normalization case |
| `sqlite-book-roundtrip` | `basic_valid.json` | minimal durable round-trip |
| `sqlite-book-roundtrip` | `correction_reason_required.json` | deterministic rejection does not persist |
| `sqlite-book-roundtrip` | `correction_target_missing.json` | deterministic rejection does not persist |
| `sqlite-book-roundtrip` | `nested_valid.json` | nested-path round-trip with optional provenance fields |
| `sqlite-book-roundtrip` | `invalid_wrong_type.json` | malformed field-type rejection case |

## Current Gaps

Not yet fuzzed:
- concurrent access between multiple writers
- CLI response rendering and envelope serialization
- failure of the `sqlite3` binary before any SQL is executed
- large-scale corpus growth around correction policy edge cases such as reversal-shape near misses
