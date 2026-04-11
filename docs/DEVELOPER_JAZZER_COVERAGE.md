---
afad: "3.5"
version: "0.5.0"
domain: DEVELOPER_JAZZER_COVERAGE
updated: "2026-04-11"
route:
  keywords: [fingrind, jazzer, coverage, harness, replay, committed-seeds, sqlite, cli, rejection]
  questions: ["what does the fingrind jazzer suite currently cover", "which committed seeds exist for fingrind fuzzing", "what is still not covered by the jazzer suite"]
---

# Jazzer Coverage Snapshot

**Purpose**: Summarize what the committed Jazzer suite covers right now.

## Harness Coverage

| Harness | Main Surface | What It Proves | Seed Count |
|:--------|:-------------|:---------------|:-----------|
| `cli-request` | `CliRequestReader.readPostEntryCommand(...)` | request parsing, CLI source stamping, forbidden committed-audit-field rejection, and legacy-field hard breaks | `8` |
| `posting-workflow` | `PostingApplicationService.preflight(...)` and `commit(...)` | application write contract, deterministic reversal rejections, and duplicate-idempotency behavior | `5` |
| `sqlite-book-roundtrip` | `SqlitePostingFactStore` plus CLI request decoding | durable round-trip in one real SQLite book file, strict-schema persistence, hardened SQLite pragmas, and no-persist deterministic rejections | `7` |

## `cli-request`

Surface:
- raw JSON bytes
- request decoding through the real CLI reader
- domain-model construction for journal lines, entries, reversal linkage, and request provenance

What it asserts:
- fresh valid requests parse successfully
- malformed JSON is normalized into `invalid-request`
- invalid domain shapes fail deterministically
- legacy `correction` request shapes are rejected deterministically
- caller-supplied `sourceChannel` is not trusted; parsed commands always carry `CLI`
- caller-supplied `recordedAt` and `sourceChannel` are rejected because they are committed fields, not request fields

## `posting-workflow`

Surface:
- `PostingApplicationService.preflight(...)`
- `PostingApplicationService.commit(...)`
- reversal admission policy
- commit through the in-memory runtime seam

What it asserts:
- fresh valid requests preflight successfully
- a first commit persists one `PostingFact`
- a duplicate commit returns `duplicate-idempotency-key`
- missing reversal reason and missing reversal target reject deterministically on both preflight and commit
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
- committed books keep both canonical tables in SQLite `STRICT` mode
- reloaded store connections keep `foreign_keys = on` and `trusted_schema = off`
- deterministic reversal rejections do not create or mutate durable book state

## Committed Seed Inventory

| Harness | Input | Meaning |
|:--------|:------|:--------|
| `cli-request` | `basic_valid.json` | minimal valid posting request |
| `cli-request` | `reversal_valid.json` | valid request carrying reversal linkage |
| `cli-request` | `invalid_legacy_correction.json` | rejected legacy correction request shape |
| `cli-request` | `invalid_forbidden_recorded_at.json` | rejected committed-audit request field |
| `cli-request` | `invalid_forbidden_source_channel.json` | rejected committed-audit request field, even as `null` |
| `cli-request` | `invalid_amount_exponent.json` | exponent notation rejection |
| `cli-request` | `invalid_missing_provenance.json` | missing provenance object |
| `cli-request` | `invalid_unbalanced.json` | unbalanced journal entry |
| `posting-workflow` | `basic_valid.json` | successful preflight then commit |
| `posting-workflow` | `reversal_reason_required.json` | deterministic rejection for missing reversal reason |
| `posting-workflow` | `reversal_target_missing.json` | deterministic rejection for missing reversal target |
| `posting-workflow` | `invalid_amount_exponent.json` | exponent notation rejection |
| `posting-workflow` | `invalid_blank_actor.json` | invalid provenance normalization case |
| `sqlite-book-roundtrip` | `basic_valid.json` | minimal durable round-trip |
| `sqlite-book-roundtrip` | `reversal_reason_required.json` | deterministic rejection does not persist |
| `sqlite-book-roundtrip` | `reversal_target_missing.json` | deterministic rejection does not persist |
| `sqlite-book-roundtrip` | `invalid_amount_exponent.json` | exponent notation rejection |
| `sqlite-book-roundtrip` | `nested_valid.json` | nested-path round-trip with optional provenance fields |
| `sqlite-book-roundtrip` | `unicode_valid.json` | Unicode round-trip through strict SQLite storage |
| `sqlite-book-roundtrip` | `invalid_wrong_type.json` | malformed field-type rejection case |

## Current Gaps

Not yet fuzzed:
- concurrent access between multiple writers
- CLI response rendering and envelope serialization
- corrupt or directory-backed pre-existing book paths before any valid schema exists
- large-scale corpus growth around reversal policy edge cases such as reversal-shape near misses
