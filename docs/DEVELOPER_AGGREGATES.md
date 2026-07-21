---
afad: "5.0.1"
version: "0.61.0"
domain: DEVELOPER_AGGREGATES
updated: "2026-07-21"
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
- protected-book maintenance
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
- Domain owners:
  - `executor.bookkeeping.BookLifecycleInspection`
  - `executor.BookAdministrationService`
  - `contract.runtime.BookFormatContract`
- Storage participants:
  - `sqlite.SqliteBookStateReader`
  - `sqlite.SqliteStoreMutationOperations`
- Notes: book lifecycle is not inferred from file existence alone. It is proved from
  `application_id`, `user_version`, schema fingerprint, foreign-key integrity, persisted-money
  integrity, and journal integrity.

## Protected-Book Maintenance Boundary

- Invariant: one closed protected book may be exported only as a manifest-attested backup pair,
  restored only from a verified backup pair, and rekeyed only through its atomic maintenance
  workflow.
- Maintenance paths: `backup-book`, `restore-book`, and `rekey-book`; read-only attestation
  inspection uses `verify-book`, `attestation-review`, and retained receipts.
- Immediate or derived: immediate.
- Domain owners:
  - `executor.ProtectedBookMaintenanceService`
  - `executor.maintenance.ProtectedBookBackupOutcome`
  - `executor.maintenance.ProtectedBookRestoreOutcome`
  - `executor.maintenance.ProtectedBookMaintenanceRejection`
- Storage participants:
  - `executor.spi.ProtectedBookMaintenanceStore`
  - `sqlite.SqliteProtectedBookMaintenanceStore`
- Notes: maintenance workflows are protected-book artifact operations, not bookkeeping mutations.
  They keep backup, restore, and rekey state explicit in their own context instead of
  leaking verification and replacement rules into SQLite adapter code or published DTO families.
  Successful maintenance audit facts now live inside the encrypted `audit_event` stream rather than
  in one adjacent plaintext maintenance journal.

## Account Registry Boundary

- Invariant: one `accountCode` identifies one declared account whose `accountType` and declared
  taxonomy are immutable after first declaration; redeclaration may reactivate the account and
  rename it, but may not rewrite classification or statement-line taxonomy. `normalBalance` is a
  derived fact from `accountType` plus the declared classification owned by
  `AccountTaxonomyDoctrine`.
- Mutation paths: `declare-account` and `declare-account` workflow steps.
- Immediate or derived: immediate.
- Domain owners:
  - `core.AccountCodePolicy`
  - `core.AccountType`
  - `executor.bookkeeping.AccountDeclaration`
  - `executor.bookkeeping.RegisteredAccount`
  - `executor.BookAdministrationService`
- Storage participants:
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
- Domain owners:
  - `core.JournalEntry`
  - `core.Money`, `core.PositiveMoney`, `core.CurrencyBalance`, `core.BalanceMath`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `executor.bookkeeping.posting.BookkeepingPostingService`
  - `executor.PostingApplicationService`
- Storage participants:
  - `sqlite.SqliteStoreMutationOperations`
  - `sqlite.SqlitePostingSql`
- Notes: read/report projections do not own ledger truth. They derive from the committed posting
  ledger.

## Reversal Relation Boundary

- Invariant: one target posting may have at most one reversal, and a reversal must negate the
  target posting exactly.
- Mutation paths: posting commit when `reversal.priorPostingId` is present.
- Immediate or derived: immediate.
- Domain owners:
  - `core.ReversalReference`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `executor.bookkeeping.PostingLineageModel`
  - `executor.bookkeeping.posting.BookkeepingPostingService`
- Storage participants:
  - `sqlite.SqlitePostingSql`
- Notes: reversal is additive lineage, not in-place correction.

## Idempotency Identity Boundary

- Invariant: one committed `idempotencyKey` is single-use inside one selected book.
- Mutation paths: posting commit only.
- Immediate or derived: immediate.
- Domain owners:
  - `core.IdempotencyKey`
  - `executor.bookkeeping.PostingAcceptancePolicy`
  - `executor.bookkeeping.posting.BookkeepingPostingService`
- Storage participants:
  - `sqlite.SqliteStoreMutationOperations`
  - SQLite unique constraint on `posting_fact.idempotency_key`
- Notes: idempotency is book-local, not global across books.

## Workflow Transaction Boundary

- Invariant: one `execute-plan` request either commits the whole accepted plan journal against one
  selected book or rolls the entire mutation set back; plan assertions and bookkeeping mutations
  share one transaction boundary.
- Mutation paths: `execute-plan`.
- Immediate or derived: immediate.
- Domain owners:
  - `executor.workflow.BookWorkflowExecutionService`
  - `executor.spi.AtomicBookStore`
- Storage participants:
  - `sqlite.SqliteStoreMutationOperations`
- Notes: workflow journals are returned after the authoritative transactional decision, not as a
  second source of bookkeeping truth.

## Audit Event Stream Boundary

- Invariant: one append-only audit stream records durable administrative and posting events in the
  same book, and audit rows cannot be updated or deleted in place.
- Mutation paths: `open-book`, `declare-account`, `post-entry`, `execute-plan`, `rekey-book`,
  `backup-book`, and `restore-book` through
  the bookkeeping/store mutation paths that actually change the book.
- Immediate or derived: immediate on write, read-only on inspection.
- Domain owners:
  - `executor.bookkeeping.BookAuditEvent`
  - `executor.bookkeeping.BookAuditEventKind`
- Storage participants:
  - `sqlite.SqliteAuditEventWriter`
  - `sqlite.SqliteStoreMutationOperations`
  - SQLite `audit_event` append-only triggers
- Notes: posting provenance inside `posting_fact` is not a substitute for this stream; account
  mutation and rekey actions must also be durable audit facts.

## Protected-Book Maintenance Audit

- Invariant: successful backup, restore, and rekey facts are durable encrypted audit events inside
  the protected book, and maintenance workflows retract those facts when a paired external file
  mutation fails before publication completes.
- Maintenance paths: mutation workflows `backup-book`, `restore-book`, and `rekey-book` through
  the maintenance service/store boundary.
- Immediate or derived: immediate on successful mutation; absent for side-effect-free inspection.
- Domain owners:
  - `executor.maintenance.ProtectedBookMaintenanceAuditKind`
  - `executor.bookkeeping.BookAuditEvent`
- Storage participants:
  - `sqlite.SqliteProtectedBookMaintenanceStore`
  - `sqlite.audit_event`
- Notes: maintenance audit belongs to the encrypted bookkeeping audit stream because the selected
  protected book is the durable state owner for successful maintenance facts.

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
