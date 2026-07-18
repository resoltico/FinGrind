---
afad: "5.0.1"
version: "0.61.0"
domain: DEVELOPER_JAZZER_COVERAGE
updated: "2026-07-16"
route:
  keywords: [fingrind, jazzer, coverage, harness, replay, committed-seeds, sqlite, cli, rejection]
  questions: ["what does the fingrind jazzer suite currently cover", "which committed seeds exist for fingrind fuzzing", "what remains uncovered by the jazzer suite"]
---

# Jazzer Coverage Snapshot

**Purpose**: Summarize what the committed Jazzer suite covers right now.

## Harness Coverage

| Harness | Main Surface | What It Proves | Seed Count |
|:--------|:-------------|:---------------|:-----------|
| `cli-request` | `CliRequestReader.readPostEntryCommand(...)` | request parsing, CLI source stamping, forbidden committed-audit-field rejection, duplicate-key rejection, unexpected-field rejection, and legacy-field hard breaks | `10` |
| `ledger-plan-request` | `CliRequestReader.readLedgerPlan(...)` plus in-memory `LedgerPlanService.execute(...)` | ledger-plan parsing, canonical step-kind preservation, successful in-memory execution, structured list-query journal facts, rejected missing-book list-query plans without fake page facts, removal of the inert execution-policy block, ensure-book ordering, explicit 100-step protocol-limit rejection, and unknown-kind error shaping without assertion fallthrough | `7` |
| `posting-workflow` | `PostingApplicationService.preflight(...)` and `commit(...)` | explicit book lifecycle rejection order, account-registry rejections, application write contract, deterministic reversal rejections, and duplicate-idempotency behavior | `5` |
| `sqlite-book-roundtrip` | `SqlitePostingSession` via `SqliteBookSessions` plus CLI request decoding | explicit SQLite book lifecycle, account-registry enforcement, durable round-trip in one real protected SQLite book file, CLI response rendering across executed read/report commands, concurrent contender handling, corrupt pre-schema path failure shaping, derived reversal near misses, strict-schema persistence, hardened SQLite pragmas, and no-persist deterministic rejections | `7` |
| `inventory-costing-math` | `WeightedAverageCostingMath.dispose(...)` plus `roundedMovingAverageUnitCostProjection(...)` | projection independence asserted through direct exact-pool cost-of-sales derivation, one per-seed generated rounded-projection mismatch family, one pinned rounded-projection mismatch case, committed replay of one mismatch seed, and pure quantity/cost-pool invariant preservation under arbitrary byte-seed generation | `1` |

## `cli-request`

Surface:
- raw JSON bytes
- request decoding through the real CLI reader
- domain-model construction for journal lines, entries, reversal linkage, and request provenance

What it asserts:
- fresh valid requests parse successfully
- malformed JSON is normalized into the `expected-invalid` replay outcome with an unparsed detail payload
- invalid domain shapes fail deterministically
- duplicate JSON object keys are rejected deterministically
- unexpected object fields are rejected deterministically
- legacy `correction` request shapes are rejected deterministically
- caller-supplied `sourceChannel` is not trusted; parsed commands always carry `CLI`
- caller-supplied `recordedAt` and `sourceChannel` are rejected because they are committed fields, not request fields

## `ledger-plan-request`

Surface:
- raw ledger-plan JSON bytes
- request decoding through the real CLI reader
- domain-model construction for operation steps, query steps, posting steps, and assertion steps

What it asserts:
- valid ledger plans parse successfully
- parsed plan ids and step kinds stay non-blank
- `ensure-book` is accepted only as the first step when present
- assertion steps keep their own canonical kind instead of collapsing to `execute-plan`
- successful `list-accounts` and `list-postings` steps keep page metadata plus structured row groups
- rejected `list-accounts` and `list-postings` steps do not pretend to carry success-only page facts
- the removed `executionPolicy` block is rejected deterministically
- oversize ledger plans are rejected at the 100-step protocol limit
- unknown `kind` typos are reported as unsupported step kinds without requiring an `assertion` object first

## `posting-workflow`

Surface:
- `PostingApplicationService.preflight(...)`
- `PostingApplicationService.commit(...)`
- reversal admission policy
- commit through the in-memory book-session seam

What it asserts:
- fresh valid requests preflight successfully
- fresh unopened books reject with `posting-book-not-initialized`
- opened books with undeclared accounts replay as `unknown-account`
- declared accounts that are later deactivated replay as `inactive-account`
- a first commit persists one `PostingFact`
- an exact-request duplicate commit replays successfully with `idempotentReplay=true`
- missing reversal reason is rejected at request parsing, and missing reversal target rejects deterministically on both preflight and commit
- stored fact shape matches the parsed command when commit succeeds

## `sqlite-book-roundtrip`

Surface:
- request parsing through the same CLI seam
- explicit `open-book` initialization through the public SQLite session factory
- explicit account declaration before durable posting
- commit and reload against a real filesystem path plus deterministic protected-book passphrase
  material
- reopening the same SQLite book file in a fresh adapter instance
- executed `inspect-book`, `list-accounts`, `get-posting`, `list-postings`, `account-balance`,
  `trial-balance`, `account-ledger`, and `period-summary` workflows through the real CLI writer
  surface
- corrupt directory-backed and plaintext pre-schema book paths through the real storage-failure
  and internal-error boundary seam
- two concurrent contenders racing one direct posting command against the same protected book
- derived exact and near-miss reversal follow-up commands against a durably committed posting

What it asserts:
- unopened books reject with `posting-book-not-initialized`
- opened books with undeclared accounts replay as `unknown-account`
- deactivated accounts replay as `inactive-account`
- one valid request commits durably into one selected initialized book file
- the durable book file is opened through SQLite3 Multiple Ciphers rather than plain SQLite
- reloading by idempotency returns the same fact shape with `idempotentReplay=true`
- same-key near-miss duplicate commits are rejected in the same book with `idempotency-key-conflict`
- parent-directory creation works for nested arbitrary paths
- read/report surfaces render non-blank machine/text/CSV documents from the real workflow path
- unknown posting and unknown-account read/report rejections render through the same owned
  envelope writers
- directory-backed and plaintext pre-schema book paths do not commit or open, and any thrown
  SQLite storage/runtime failures render through the owned CLI failure envelope
- two concurrent contenders leave one durable winning posting fact and one deterministic
  non-winning outcome without surfacing a bug
- derived reversal near misses reject with `reversal-does-not-negate-target`
- a valid derived reversal commits once and a second exact reversal rejects with
  `reversal-already-exists`
- committed books keep `book_meta`, `account`, `posting_fact`, and `journal_line` in SQLite `STRICT` mode
- committed books keep the `journal_line.account_code -> account.account_code` foreign key
- reloaded store connections keep `foreign_keys = on` and `trusted_schema = off`
- deterministic reversal rejections do not create or mutate durable book state

## `inventory-costing-math`

Surface:
- raw byte seeds expanded into deterministic quantity-scale, quantity, and unit-cost scenarios
- pure `WeightedAverageCostingMath.acquire(...)`, `dispose(...)`, and `roundedMovingAverageUnitCostProjection(...)`
- exact `Quantity` plus `Money` state with no SQLite, CLI, or workflow I/O

What it asserts:
- one known weighted-average mismatch case keeps exact cost of sales distinct from one rounded
  projection-based surrogate
- arbitrary generated pools and disposals keep exact cost of sales equal to
  `roundHalfUp(cost_pool × qtyDisposed / qtyOnHand)` over the exact pool and exact quantity
- arbitrary generated pools preserve the zero-to-zero quantity and cost-pool truth after disposal
- byte-seed variation does not turn this pure math harness into a parser or storage seam

## Committed Seed Inventory

| Harness | Input | Meaning |
|:--------|:------|:--------|
| `cli-request` | `basic_valid.json` | minimal valid posting request |
| `cli-request` | `reversal_valid.json` | valid request carrying reversal linkage |
| `cli-request` | `invalid_legacy_correction.json` | rejected legacy correction request shape |
| `cli-request` | `invalid_forbidden_recorded_at.json` | removed `recordedAt` field rejection |
| `cli-request` | `invalid_forbidden_source_channel.json` | removed `sourceChannel` field rejection, even when `null` |
| `cli-request` | `invalid_amount_exponent.json` | exponent notation rejection |
| `cli-request` | `invalid_duplicate_idempotency_key.json` | duplicate JSON object key rejection |
| `cli-request` | `invalid_missing_provenance.json` | missing provenance object |
| `cli-request` | `invalid_unexpected_top_level_field.json` | unexpected request field rejection |
| `cli-request` | `invalid_unbalanced.json` | unbalanced journal entry |
| `ledger-plan-request` | `basic_valid.json` | valid plan with operation, posting, and assertion steps |
| `ledger-plan-request` | `query_valid.json` | valid plan with successful structured list-query journal facts |
| `ledger-plan-request` | `rejected_missing_book_list_postings.json` | parsed plan rejects as missing-book query workflow without fabricated pagination facts |
| `ledger-plan-request` | `invalid_execution_policy.json` | removed execution-policy block rejection |
| `ledger-plan-request` | `invalid_ensure_book_not_first.json` | ensure-book ordering rejection |
| `ledger-plan-request` | `invalid_too_many_steps.json` | 100-step protocol limit rejection |
| `ledger-plan-request` | `invalid_unknown_kind_without_assertion.json` | unknown kind rejection without assertion fallthrough |
| `posting-workflow` | `basic_valid.json` | successful four-line preflight then commit with optional correlation id |
| `posting-workflow` | `invalid_missing_reversal_reason.json` | invalid request for missing reversal reason inside `reversal` |
| `posting-workflow` | `reversal_target_missing.json` | deterministic rejection for missing reversal target |
| `posting-workflow` | `invalid_amount_exponent.json` | exponent notation rejection with reversal payload present |
| `posting-workflow` | `invalid_blank_actor.json` | blank actor-id rejection |
| `sqlite-book-roundtrip` | `basic_valid.json` | minimal durable round-trip with distinct system provenance |
| `sqlite-book-roundtrip` | `invalid_missing_reversal_reason.json` | invalid request for missing reversal reason inside `reversal` |
| `sqlite-book-roundtrip` | `reversal_target_missing.json` | missing reversal target rejects commit without persisting facts |
| `sqlite-book-roundtrip` | `invalid_amount_exponent.json` | exponent notation rejection with optional provenance correlation |
| `sqlite-book-roundtrip` | `nested_valid.json` | nested-path round-trip with optional provenance fields |
| `sqlite-book-roundtrip` | `invalid_unicode_account_code.json` | invalid Unicode account-code rejection through the strict SQLite request path |
| `sqlite-book-roundtrip` | `invalid_wrong_type.json` | `effectiveDate` wrong-type rejection |
| `inventory-costing-math` | `exact_pool_math_seed.bin` | pinned byte-seed replay of the known rounded-projection mismatch case where exact disposal cost stays distinct from one projection-based surrogate |

## Remaining Gap Register

No known open committed-harness gaps are recorded in the current Jazzer surface, and the current
seed audit reports no duplicate-content defects, no orphaned committed inputs, and no committed
`unexpected-failure` expectations.
Add entries here only when a concrete uncovered behavior is proven against the live harness set.
