---
afad: "3.5"
version: "0.1.0"
domain: DEVELOPER_JAZZER_COVERAGE
updated: "2026-04-09"
route:
  keywords: [fingrind, jazzer, coverage, harnesses, committed-seeds, gaps, parser, workflow, sqlite]
  questions: ["which fuzz harnesses exist in fingrind", "what committed seeds does fingrind jazzer ship", "what fuzzing gaps remain in fingrind"]
---

# Jazzer Coverage Inventory

**Purpose**: Current inventory of FinGrind fuzz coverage, committed seed stock, and known gaps.
**Architecture reference**: [DEVELOPER_JAZZER.md](./DEVELOPER_JAZZER.md)
**Operator reference**: [DEVELOPER_JAZZER_OPERATIONS.md](./DEVELOPER_JAZZER_OPERATIONS.md)

## Coverage Summary

| Target | Entry Point | Concern | Committed Seeds |
|:-------|:------------|:--------|:----------------|
| `cli-request` | `CliRequestReader.readPostEntryCommand(...)` through `CliFuzzSupport` | malformed request bytes, JSON structure, domain validation | 4 |
| `posting-workflow` | `PostingApplicationService.preflight(...)` and `commit(...)` | application write contract and duplicate-idempotency behavior | 3 |
| `sqlite-book-roundtrip` | `SqlitePostingFactStore` plus `PostingApplicationService` | durable single-book persistence at arbitrary filesystem paths | 3 |
| `regression` | `JazzerRegressionRunner` over committed seed directories plus regression metadata | deterministic replay of the committed seed floor | 10 total |

## Harness Matrix

### `cli-request`

Surface:
- raw JSON bytes
- malformed syntax and malformed byte encodings
- effective-date parsing
- journal-line and provenance validation
- correction-link payload shape

What it asserts:
- no unexpected crash during request decoding
- valid payloads produce a non-null `PostEntryCommand`
- invalid payloads fail as deterministic invalid-request outcomes

### `posting-workflow`

Surface:
- request parsing through the same CLI seam
- `PostingApplicationService.preflight(...)`
- `PostingApplicationService.commit(...)`
- duplicate-idempotency behavior in one in-memory book

What it asserts:
- fresh valid requests preflight successfully
- a first commit persists one `PostingFact`
- a duplicate commit returns `DUPLICATE_IDEMPOTENCY_KEY`
- stored fact shape matches the parsed command

### `sqlite-book-roundtrip`

Surface:
- request parsing through the same CLI seam
- `SqlitePostingFactStore` schema bootstrap
- commit and reload against a real filesystem path
- reopening the same SQLite book file in a fresh adapter instance

What it asserts:
- one valid request commits durably into one selected book file
- reloading by idempotency returns the same fact shape
- duplicate commit attempts are rejected in the same book
- parent-directory creation works for nested arbitrary paths

## Deterministic Support Tests

The nested Jazzer build also includes deterministic support tests under:

```text
jazzer/src/test/java/dev/erst/fingrind/jazzer/
```

Current support scope:
- `JazzerHarnessRunnerTest`: launcher argument parsing, selected-class execution semantics, and
  in-flight harness pulse behavior
- `JazzerRegressionRunnerTest`: committed metadata replay contract and regression-target pulse shape
- `JazzerReplaySupportTest`: direct replay classification for valid and deterministic-invalid seeds
- `RegressionSeedMetadataTest`: committed metadata completeness and path hygiene

## Committed Seed Inventory

| Harness | Input | Meaning |
|:--------|:------|:--------|
| `cli-request` | `basic_valid.json` | minimal valid posting request |
| `cli-request` | `correction_valid.json` | valid request carrying correction linkage |
| `cli-request` | `invalid_missing_provenance.json` | missing provenance object |
| `cli-request` | `invalid_unbalanced.json` | unbalanced journal entry |
| `posting-workflow` | `basic_valid.json` | successful preflight then commit |
| `posting-workflow` | `correction_valid.json` | successful correction-linked workflow |
| `posting-workflow` | `invalid_blank_actor.json` | invalid provenance normalization case |
| `sqlite-book-roundtrip` | `basic_valid.json` | minimal durable round-trip |
| `sqlite-book-roundtrip` | `nested_valid.json` | nested-path round-trip with optional provenance fields |
| `sqlite-book-roundtrip` | `invalid_wrong_type.json` | malformed field-type rejection case |

## Findings Fixed So Far

The first active fuzz loop already produced and resolved real parser issues:
- malformed JSON syntax is now normalized into invalid-request failure
- malformed date/time text is now normalized into invalid-request failure
- invalid byte-encoding payloads are now normalized into invalid-request failure

## Current Gaps

Not yet fuzzed:
- multi-command batch surfaces beyond one request at a time
- concurrent access between multiple writers
- absence or corruption of the external `sqlite3` binary itself
- schema evolution and country-pack extensions
- CLI JSON response rendering
