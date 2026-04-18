---
afad: "3.5"
version: "0.17.0"
domain: INDEX
updated: "2026-04-17"
route:
  keywords: [fingrind, index, routing, api, symbols, core, protocol-catalog, contract, executor, machine-contract, query-session, adapters]
  questions: ["where is the fingrind api documented", "which doc file covers BookQuerySession", "which doc file covers RequestProvenance", "which doc file covers ProtocolCatalog", "which doc file covers LedgerPlanService"]
---

# API Index

**Purpose**: Routing table for the current public FinGrind API reference.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | accounting value objects, positive journal-line money, journal grammar, reversal linkage, request and committed provenance |
| `DOC_02_Application.md` | contract-owned protocol metadata, public request/result models, ledger plans, machine contract, and executor services |
| `DOC_03_BookSessionsAndAdapters.md` | committed facts, executor-owned seams, SQLite adapter, CLI entrypoint |

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `ProtocolCatalog` | `DOC_02_Application.md` | `ProtocolCatalog` |
| `ProtocolOperation` | `DOC_02_Application.md` | `ProtocolOperation` |
| `OperationId` | `DOC_02_Application.md` | `OperationId` |
| `OperationCategory` | `DOC_02_Application.md` | `OperationCategory` |
| `ExecutionMode` | `DOC_02_Application.md` | `ExecutionMode` |
| `ProtocolLimits` | `DOC_02_Application.md` | `ProtocolLimits` |
| `ProtocolOptions` | `DOC_02_Application.md` | `ProtocolOptions` |
| `BookModelFacts` | `DOC_02_Application.md` | `BookModelFacts` |
| `CurrencyFacts` | `DOC_02_Application.md` | `CurrencyFacts` |
| `PreflightFacts` | `DOC_02_Application.md` | `PreflightFacts` |
| `PlanExecutionFacts` | `DOC_02_Application.md` | `PlanExecutionFacts` |
| `ProtocolStatuses` | `DOC_02_Application.md` | `ProtocolStatuses` |
| `ProtocolLedgerPlanFields` | `DOC_02_Application.md` | `ProtocolLedgerPlanFields` |
| `AccountCode` | `DOC_01_Core.md` | `AccountCode` |
| `AccountName` | `DOC_01_Core.md` | `AccountName` |
| `ActorId` | `DOC_01_Core.md` | `ActorId` |
| `ActorType` | `DOC_01_Core.md` | `ActorType` |
| `CausationId` | `DOC_01_Core.md` | `CausationId` |
| `CommandId` | `DOC_01_Core.md` | `CommandId` |
| `CommittedProvenance` | `DOC_01_Core.md` | `CommittedProvenance` |
| `ReversalReason` | `DOC_01_Core.md` | `ReversalReason` |
| `ReversalReference` | `DOC_01_Core.md` | `ReversalReference` |
| `CorrelationId` | `DOC_01_Core.md` | `CorrelationId` |
| `CurrencyCode` | `DOC_01_Core.md` | `CurrencyCode` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `JournalEntry` | `DOC_01_Core.md` | `JournalEntry` |
| `JournalLine` | `DOC_01_Core.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core.md` | `JournalLine.EntrySide` |
| `Money` | `DOC_01_Core.md` | `Money` |
| `PositiveMoney` | `DOC_01_Core.md` | `PositiveMoney` |
| `NormalBalance` | `DOC_01_Core.md` | `NormalBalance` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `SourceChannel` | `DOC_01_Core.md` | `SourceChannel` |
| `BookAdministrationService` | `DOC_02_Application.md` | `BookAdministrationService` |
| `DeclareAccountCommand` | `DOC_02_Application.md` | `DeclareAccountCommand` |
| `DeclaredAccount` | `DOC_02_Application.md` | `DeclaredAccount` |
| `OpenBookResult` | `DOC_02_Application.md` | `OpenBookResult` |
| `DeclareAccountResult` | `DOC_02_Application.md` | `DeclareAccountResult` |
| `RekeyBookResult` | `DOC_02_Application.md` | `RekeyBookResult` |
| `BookInspection` | `DOC_02_Application.md` | `BookInspection` |
| `BookQueryService` | `DOC_02_Application.md` | `BookQueryService` |
| `ListAccountsQuery` | `DOC_02_Application.md` | `ListAccountsQuery` |
| `AccountPage` | `DOC_02_Application.md` | `AccountPage` |
| `ListAccountsResult` | `DOC_02_Application.md` | `ListAccountsResult` |
| `GetPostingResult` | `DOC_02_Application.md` | `GetPostingResult` |
| `ListPostingsQuery` | `DOC_02_Application.md` | `ListPostingsQuery` |
| `PostingPage` | `DOC_02_Application.md` | `PostingPage` |
| `ListPostingsResult` | `DOC_02_Application.md` | `ListPostingsResult` |
| `AccountBalanceQuery` | `DOC_02_Application.md` | `AccountBalanceQuery` |
| `CurrencyBalance` | `DOC_02_Application.md` | `CurrencyBalance` |
| `AccountBalanceSnapshot` | `DOC_02_Application.md` | `AccountBalanceSnapshot` |
| `AccountBalanceResult` | `DOC_02_Application.md` | `AccountBalanceResult` |
| `BookAdministrationRejection` | `DOC_02_Application.md` | `BookAdministrationRejection` |
| `BookQueryRejection` | `DOC_02_Application.md` | `BookQueryRejection` |
| `RejectionNarrative` | `DOC_02_Application.md` | `RejectionNarrative` |
| `PostEntryCommand` | `DOC_02_Application.md` | `PostEntryCommand` |
| `PostingRequest` | `DOC_02_Application.md` | `PostingRequest` |
| `PostingDraft` | `DOC_02_Application.md` | `PostingDraft` |
| `PostEntryResult` | `DOC_02_Application.md` | `PostEntryResult` |
| `PreflightEntryResult` | `DOC_02_Application.md` | `PreflightEntryResult` |
| `CommitEntryResult` | `DOC_02_Application.md` | `CommitEntryResult` |
| `MachineContract` | `DOC_02_Application.md` | `MachineContract` |
| `LedgerPlan` | `DOC_02_Application.md` | `LedgerPlan` |
| `LedgerStep` | `DOC_02_Application.md` | `LedgerStep` |
| `LedgerAssertion` | `DOC_02_Application.md` | `LedgerAssertion` |
| `LedgerJournalEntry` | `DOC_02_Application.md` | `LedgerJournalEntry` |
| `LedgerExecutionJournal` | `DOC_02_Application.md` | `LedgerExecutionJournal` |
| `LedgerPlanResult` | `DOC_02_Application.md` | `LedgerPlanResult` |
| `PostingApplicationService` | `DOC_02_Application.md` | `PostingApplicationService` |
| `LedgerPlanService` | `DOC_02_Application.md` | `LedgerPlanService` |
| `PostingIdGenerator` | `DOC_02_Application.md` | `PostingIdGenerator` |
| `PostingRejection` | `DOC_02_Application.md` | `PostingRejection` |
| `UuidV7PostingIdGenerator` | `DOC_02_Application.md` | `UuidV7PostingIdGenerator` |
| `App` | `DOC_03_BookSessionsAndAdapters.md` | `App` |
| `BookAccess` | `DOC_03_BookSessionsAndAdapters.md` | `BookAccess` |
| `BookAdministrationSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookAdministrationSession` |
| `PostingValidationBook` | `DOC_03_BookSessionsAndAdapters.md` | `PostingValidationBook` |
| `PostingBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `PostingBookSession` |
| `BookQuerySession` | `DOC_03_BookSessionsAndAdapters.md` | `BookQuerySession` |
| `LedgerPlanSession` | `DOC_03_BookSessionsAndAdapters.md` | `LedgerPlanSession` |
| `InMemoryBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `InMemoryBookSession` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `SqliteBookPassphrase` | `DOC_03_BookSessionsAndAdapters.md` | `SqliteBookPassphrase` |
| `SqlitePostingFactStore` | `DOC_03_BookSessionsAndAdapters.md` | `SqlitePostingFactStore` |
