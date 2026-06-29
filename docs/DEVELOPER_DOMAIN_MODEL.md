---
afad: "4.0"
version: "0.58.0"
domain: DEVELOPER_DOMAIN_MODEL
updated: "2026-06-29"
route:
  keywords: [fingrind, domain model, bounded context, context map, ubiquitous language, bookkeeping, workflow, published language]
  questions: ["what are fingrind's bounded contexts", "what is the context map in fingrind", "which term is canonical for the owner of a book", "how does execute-plan relate to bookkeeping in fingrind"]
---

# Domain Model Reference

**Purpose**: Canonical domain language, bounded contexts, and context-map reference for FinGrind.
**Companion documents**:
- [DEVELOPER.md](./DEVELOPER.md)
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md)
- [DOC_01_DecimalBoundaries.md](./DOC_01_DecimalBoundaries.md)
- [DEVELOPER_AGGREGATES.md](./DEVELOPER_AGGREGATES.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DOC_03_BookSessionsAndAdapters.md](./DOC_03_BookSessionsAndAdapters.md)

## Canonical Vocabulary

FinGrind records one protected book for one **accounting entity**.

That term is canonical across user-facing descriptions, developer theory, and machine-contract
facts:
- one book belongs to one accounting entity
- the selected SQLite path is the durable identity of that accounting entity's book
- account codes, postings, balances, and reports are book-local to that accounting entity

Retired wording:
- `business` is not the canonical owner term for a book
- bare `entity` is too loose on its own when the intended meaning is accounting scope

## Bounded Contexts

### Public Bookkeeping Protocol Context

The `contract.bookkeeping` package hosts one published bookkeeping protocol context:
- public write DTOs such as `DeclareAccountCommand` and `PostEntryCommand`
- public read/report DTOs such as `DeclaredAccount`, `PostingFact`, `PostingPage`, and the report
  result families
- deterministic administration, query, and posting rejection vocabulary

This context is the external bookkeeping contract. It is not the working bookkeeping model.

### Public Workflow Protocol Context

The `contract.workflow` package hosts one published workflow protocol context:
- `LedgerPlan`, `LedgerStep`, and `LedgerAssertion` as the accepted workflow request language
- `LedgerJournalStep`, `LedgerJournalEntry`, `LedgerExecutionJournal`, `LedgerStepFailure`, and
  `LedgerPlanResult` as the returned public execution record
- the stable wire vocabularies that describe public workflow/journal kinds and statuses

This context is the external workflow contract. It is not the internal workflow step or journal
model used during execution.

### Runtime And Discovery Contract Context

The `contract.discovery` and `contract.runtime` packages host the runtime/discovery contract
context:
- protocol metadata, help/catalog descriptors, machine-contract descriptors, and discovery
  namespaces
- runtime/distribution/storage descriptors and environment facts
- public workflow scaffolds and template descriptors

This context is public and machine-facing, but it is not bookkeeping meaning and not workflow
execution state.

### Protected-Book Maintenance Context

The protected-book maintenance context lives across:
- `executor/src/main/java/dev/erst/fingrind/executor/ProtectedBookMaintenanceService.java`
- `executor/src/main/java/dev/erst/fingrind/executor/maintenance/`
- `executor/src/main/java/dev/erst/fingrind/executor/spi/ProtectedBookMaintenanceStore.java`

This context owns:
- backup, restore, and rekey-recovery acceptance semantics
- the local maintenance rejection and verification-failure language
- the distinction between verified live books, verified backup sources, rollback artifacts, and
  restored targets
- reversible replacement discipline for destructive restore-style workflows
- encrypted in-book maintenance audit meaning plus compensating audit retraction when one external
  maintenance mutation fails after the audit is staged

This context uses `ProtectedBookBackupOutcome`, `ProtectedBookRestoreOutcome`,
`ProtectedBookRecoveryOutcome`, `ProtectedBookMaintenanceRejection`,
`ProtectedBookVerificationFailure`, `ProtectedBookMaintenanceAuditKind`, `ProtectedBookAccess`,
`MaintenanceDecision`, and `MaintenanceFailure` as its local language.

The maintenance context is not bookkeeping. It does not own accounts, postings, or reports. It
owns protected-book artifact verification and closed-copy maintenance workflows around one book.

### Bookkeeping Context

The bookkeeping context lives across:
- `core/` for accounting primitives and invariants
- `executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/` for the internal bookkeeping
  language used by application services and stores

This context owns:
- account declaration and reactivation semantics
- account classification through `AccountType`
- the current hierarchical-chart and opaque-account-code policy through `AccountCodePolicy`
- posting acceptance rules
- first-class source-document and approval evidence attached to accepted accounting facts
- local book lifecycle inspection snapshots and local query/commit rejection families
- committed posting shape used inside execution and storage
- durable append-only audit events for book opens, account mutations, posting commits, reversals,
  and rekeys
- book-local invariants such as duplicate idempotency rejection, reversal admissibility, and
  account activity requirements

The bookkeeping context uses `RegisteredAccount`, `PostingCommand`, `CommittedPosting`,
`PostingLineageModel`, `BookOpeningOutcome`, `AccountDeclarationOutcome`,
`BookLifecycleInspection`, `BookkeepingQueryRejection`, and `BookkeepingPostingRejection` as its
local language.

The shared kernel in `core/` owns `CurrencyBalance` and `EffectiveDateRange`. Public interaction
limits such as paging, request-size, passphrase-byte, and ledger-plan step bounds now live in the
protocol boundary as `ProtocolInteractionLimits`, which keeps transport policy out of the domain
core while leaving shared accounting meaning in `core/`.

Current account-registry policy:
- `AccountType` is first-class and immutable after first declaration
- `NormalBalance` is also immutable after first declaration
- account codes are opaque book-local identifiers, not semantic numeric ranges
- declared accounts may carry parent-child hierarchy through `AccountTaxonomy.parentAccountCode()`
- declared accounts carry first-class statement taxonomy through
  `financialPositionLineClassification` or `profitAndLossLineClassification`
- built-in reporting now carries current/non-current and profit-and-loss taxonomy for the current
  internal statement set, while richer disclosure and external presentation contexts remain
  separate work

Exact money belongs in that shared kernel. Future tax rates, exchange rates, percentages, and
other non-money decimal factors do not. When those domains arrive, they must enter as separate
closed types in their owning contexts instead of reusing `Money` or `MonetaryAmount`.
The shared-kernel `CurrencyUnit` truth is a repository-owned currency registry snapshot rather than
mutable host-JVM currency runtime data.

Current accounting-standards scope:
- FinGrind's current core is one narrow built-in bookkeeping kernel, not one full external IFRS
  or local-GAAP compliance/reporting package
- FinGrind does not yet claim IFRS for SMEs parity; the current kernel sits below one full
  small-entity reporting package
- the current built-in reporting surface is financial position, income statement, cash receipts
  and payments, and changes in equity
- comparative windows and comparative payload data for those built-in statements are derived from
  one book's declared fiscal-year anchor through the built-in bookkeeping kernel
- OCI, note/disclosure packages, and multi-currency translation remain separate future domains
- tax, invoicing / receivables / payables, inventory, payroll, and group reporting remain adjacent
  future contexts above the current kernel

Adjacent future contexts implied by the current boundary may emerge later, but they are not
published protocol seams until they own executable commands, state, storage, and tests.

### Workflow Context

The workflow context lives in
`executor/src/main/java/dev/erst/fingrind/executor/workflow/`.

This context owns orchestration semantics for AI-authored or automation-authored plans:
- ordered steps
- assertions
- workflow-owned per-step machine facts
- local workflow journal descriptors, failures, entries, and execution journals such as
  `BookWorkflowJournalEntry` and `BookWorkflowExecutionJournal`
- workflow-local plan ids and step ids
- translation from public `LedgerPlan` payloads into executable internal workflow steps
- translation from local workflow journals, failures, and facts back into the published
  `LedgerJournal*`, `LedgerFact`, and `LedgerPlanResult` families

Assertions and step ordering belong here, not inside the bookkeeping model.

### Host And Adapter Contexts

`cli/` and `sqlite/` are host/adaptor contexts:
- `cli/` accepts or renders the published language
- `sqlite/` persists and retrieves bookkeeping state, verifies protected-book artifacts, and
  records maintenance audit facts inside the encrypted live-book audit stream

They do not redefine bookkeeping rules or public workflow vocabulary.

## Context Map

```text
CLI / bundle / container
        |
        v
Published contract subcontexts (`contract`)
  - bookkeeping protocol
  - workflow protocol
  - runtime/discovery contract
        |  published-language translators
        |  / anti-corruption layer
        +-------------------------------+
        |                               |
        v
Protected-book maintenance context (`executor.maintenance`)
        |  maintenance store port
        v
SQLite maintenance adapter (`sqlite`)
        ^
        |
Bookkeeping context (`core` + `executor.bookkeeping`)
        ^
        |
Workflow context (`executor.workflow`)
        |
        v
SQLite bookkeeping adapter (`sqlite`)
```

Interpretation:
- the CLI speaks the public protocol context
- translator classes are the anti-corruption layer between the published bookkeeping/workflow
  languages and the internal bookkeeping/workflow/maintenance contexts
- `ProtectedBookMaintenanceService` is the thin published-language adapter over the local
  maintenance context plus the `ProtectedBookMaintenanceStore` SPI
- `BookReadService` is the thin published-language adapter over the local
  `executor.bookkeeping.read.BookkeepingReadService`
- `PostingApplicationService` is the thin published-language adapter over the local
  `executor.bookkeeping.posting.BookkeepingPostingService`
- `LedgerPlanService` is the thin published-language adapter over the local
  `executor.workflow.BookWorkflowExecutionService`
- `BookWorkflowPublishedLanguageTranslator` and
  `BookWorkflowPublishedJournalTranslator` together form the workflow-side anti-corruption layer
  at the published-language edge
- the local maintenance service owns backup, restore, rollback selection, replacement, and
  verification semantics before any public maintenance DTO or rejection family is projected
- the local bookkeeping services own inspection, query, reporting, preflight, and commit semantics
  before any public DTO or public rejection family is projected; statement doctrine is now split
  across `FinancialPositionStatementCalculator`, `IncomeStatementCalculator`, and
  `ChangesInEquityStatementCalculator` inside `executor.bookkeeping.reporting`, while
  `BookkeepingReportingService` coordinates that local reporting slice and
  `executor.bookkeeping.read.BookkeepingReadService` remains an orchestrator over query and
  reporting outcomes
- `execute-plan` enters through the public workflow schema, then runs as internal workflow steps
  plus workflow-owned `BookWorkflowFact` observations before the public journal/result surface is
  projected back out
- SQLite persists bookkeeping state, serves the executor-owned local inspection/read/write models
  rather than public report DTOs directly, and implements the maintenance store plus encrypted
  maintenance-audit persistence without owning maintenance semantics

## Ownership Rules

- Public bookkeeping and workflow DTOs are not the internal working model of bookkeeping or
  workflow code.
- The split `contract.bookkeeping`, `contract.workflow`, `contract.discovery`, and
  `contract.runtime` packages are published-language boundaries, not one shared local model.
- Bookkeeping invariants must live with bookkeeping aggregates or bookkeeping policies, not inside
  the SQLite adapter.
- Maintenance invariants must live with the protected-book maintenance context, not inside the
  SQLite adapter and not inside published DTO/result types.
- `Money` / `PositiveMoney` / `MonetaryAmount` are only for posted monetary facts, not for tax
  rates, percentages, exchange rates, or generic decimal factors.
- Workflow semantics must live with workflow types and services, not inside bookkeeping value
  objects.
- Workflow facts and failure payloads belong to the workflow context and are translated to public
  `LedgerFact` and `LedgerStepFailure` only at the published-language edge.
- Public read/report DTOs and public workflow journal/result DTOs belong only at boundary
  translators and exported application services.
- New transport layers or tools must translate at the boundary instead of importing protocol DTOs
  as their local model.

## Cue For Future Refactors

When a change touches business meaning, ask these questions before editing:
- Is this concept part of the public published language, the bookkeeping model, or the workflow
  model?
- Does the change alter one accounting entity's book semantics, or only transport/orchestration?
- Which translator or boundary should absorb the shape change?

If the answer is unclear, the context map above is the first place to repair before adding more
types.
