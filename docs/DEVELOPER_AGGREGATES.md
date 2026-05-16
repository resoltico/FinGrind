---
afad: "4.0"
version: "0.38.0"
domain: DEVELOPER_AGGREGATES
updated: "2026-05-16"
route:
  keywords: [fingrind, aggregates, consistency boundary, bookkeeping, workflow, account registry, posting ledger, audit stream, idempotency]
  questions: ["what are fingrind's aggregate boundaries", "which service owns a bookkeeping invariant in fingrind", "where is transaction consistency enforced in fingrind"]
---

# Aggregate And Consistency Boundary Reference

**Purpose**: Name the current FinGrind consistency boundaries, the invariants they protect, and
the code paths that are allowed to mutate them.
**Companion documents**:
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)

## Boundary Map

FinGrind's current immediate-consistency boundaries are:
- book lifecycle
- account registry
- posting ledger
- reversal relation
- idempotency identity
- workflow transaction
- audit event stream

Those boundaries are small on purpose. FinGrind prefers immediate consistency only where one true
bookkeeping invariant requires it. Everything else is derived at read time from the durable book.

## Book Lifecycle Boundary

- Invariant: one selected path is either missing, blank SQLite, initialized FinGrind, foreign
  SQLite, unsupported format, or incomplete FinGrind; commands must not guess.
- Mutation paths: `open-book`, `rekey-book`, and the internal plan transaction lifecycle.
- Immediate or derived: immediate.
- Primary owners:
  - `executor.bookkeeping.BookLifecycleInspection`
  - `sqlite.SqliteBookStateReader`
  - `sqlite.SqliteStoreMutationOperations`
  - `contract.runtime.BookFormatContract`
- Notes: book lifecycle is not inferred from file existence alone. It is proved from
  `application_id`, `user_version`, schema fingerprint, foreign-key integrity, persisted-money
  integrity, and journal integrity.

## Account Registry Boundary

- Invariant: one `accountCode` identifies one declared account whose `accountType`,
  `accountRole`, and declared taxonomy are immutable after first declaration; redeclaration may
  reactivate the account and rename it, but may not rewrite classification, doctrinal role, or
  statement-line taxonomy. `normalBalance` is a derived fact from `accountType` plus
  `accountRole`.
- Mutation paths: `declare-account` and `declare-account` workflow steps.
- Immediate or derived: immediate.
- Primary owners:
  - `core.AccountCodePolicy`
  - `core.AccountType`
  - `executor.bookkeeping.AccountDeclaration`
  - `executor.bookkeeping.RegisteredAccount`
  - `executor.BookAdministrationService`
  - `sqlite.SqliteStoreMutationOperations`
- Notes: current FinGrind books use explicit parent-child hierarchy and statement taxonomy while
  keeping account-code text opaque and book-local rather than type-carrying numeric ranges.

## Posting Ledger Boundary

- Invariant: one committed posting is balanced, single-currency, append-only, references declared
  active accounts, respects duplicate-idempotency rules, and persists one immutable committed fact
  plus its journal lines atomically.
- Mutation paths: `post-entry`, `preflight-entry` validation before commit, and workflow posting
  steps inside `execute-plan`.
- Immediate or derived: immediate for commit acceptance; derived for reports.
- Primary owners:
  - `core.JournalEntry`
  - `core.Money`, `core.PositiveMoney`, `core.CurrencyBalance`, `core.BalanceMath`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `executor.PostingApplicationService`
  - `sqlite.SqliteStoreMutationOperations`
  - `sqlite.SqlitePostingSql`
- Notes: read/report projections do not own ledger truth. They derive from the committed posting
  ledger.

## Reversal Relation Boundary

- Invariant: one target posting may have at most one reversal, and a reversal must negate the
  target posting exactly.
- Mutation paths: posting commit when `reversal.priorPostingId` is present.
- Immediate or derived: immediate.
- Primary owners:
  - `core.ReversalReference`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `executor.bookkeeping.PostingLineageModel`
  - `sqlite.SqlitePostingSql`
- Notes: reversal is additive lineage, not in-place correction.

## Idempotency Identity Boundary

- Invariant: one committed `idempotencyKey` is single-use inside one selected book.
- Mutation paths: posting commit only.
- Immediate or derived: immediate.
- Primary owners:
  - `core.IdempotencyKey`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `sqlite.SqliteStoreMutationOperations`
  - SQLite unique constraint on `posting_fact.idempotency_key`
- Notes: idempotency is book-local, not global across books.

## Workflow Transaction Boundary

- Invariant: one `execute-plan` request either commits the whole accepted plan journal against one
  selected book or rolls the entire mutation set back; plan assertions and bookkeeping mutations
  share one transaction boundary.
- Mutation paths: `execute-plan`.
- Immediate or derived: immediate.
- Primary owners:
  - `executor.workflow.BookWorkflowExecutionService`
  - `executor.spi.AtomicBookStore`
  - `sqlite.SqliteStoreMutationOperations`
- Notes: workflow journals are returned after the authoritative transactional decision, not as a
  second source of bookkeeping truth.

## Audit Event Stream Boundary

- Invariant: one append-only audit stream records durable administrative and posting events in the
  same book, and audit rows cannot be updated or deleted in place.
- Mutation paths: `open-book`, `declare-account`, `post-entry`, `execute-plan`, and `rekey-book`
  through the bookkeeping/store mutation paths that actually change the book.
- Immediate or derived: immediate on write, read-only on inspection.
- Primary owners:
  - `executor.bookkeeping.BookAuditEvent`
  - `executor.bookkeeping.BookAuditEventKind`
  - `sqlite.SqliteAuditEventWriter`
  - `sqlite.SqliteStoreMutationOperations`
  - SQLite `audit_event` append-only triggers
- Notes: posting provenance inside `posting_fact` is not a substitute for this stream; account
  mutation and rekey actions must also be durable audit facts.

## Read Models And Reports

`list-accounts`, `get-posting`, `list-postings`, `account-balance`, `trial-balance`,
`account-ledger`, and `period-summary` are not aggregate boundaries.

They are derived views over:
- the account registry
- the posting ledger
- the audit event stream where relevant

Their owners are read services and translators such as:
- `executor.BookReadService`
- `executor.bookkeeping.BookkeepingReadService`
- `sqlite.SqlitePostingReader`
- `sqlite.SqliteAccountLedgerReader`
- `sqlite.SqlitePeriodSummaryReader`

Those surfaces must not acquire write-side invariants that belong to the aggregate boundaries
above.
