---
afad: "3.5"
version: "0.11.0"
domain: INDEX
updated: "2026-04-13"
route:
  keywords: [fingrind, index, routing, api, symbols, core, application, machine-contract, book-session, adapters]
  questions: ["where is the fingrind api documented", "which doc file covers BookSession", "which doc file covers RequestProvenance"]
---

# API Index

**Purpose**: Routing table for the current public FinGrind API reference.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | accounting value objects, account-registry vocabulary, journal grammar, reversal linkage, request and committed provenance |
| `DOC_02_Application.md` | book-lifecycle commands, write-boundary commands, results, rejection taxonomy, machine contract, services |
| `DOC_03_BookSessionsAndAdapters.md` | committed facts, book-session seam, SQLite adapter, CLI entrypoint |

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
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
| `NormalBalance` | `DOC_01_Core.md` | `NormalBalance` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `SourceChannel` | `DOC_01_Core.md` | `SourceChannel` |
| `BookAdministrationService` | `DOC_02_Application.md` | `BookAdministrationService` |
| `DeclareAccountCommand` | `DOC_02_Application.md` | `DeclareAccountCommand` |
| `DeclaredAccount` | `DOC_02_Application.md` | `DeclaredAccount` |
| `OpenBookResult` | `DOC_02_Application.md` | `OpenBookResult` |
| `DeclareAccountResult` | `DOC_02_Application.md` | `DeclareAccountResult` |
| `ListAccountsResult` | `DOC_02_Application.md` | `ListAccountsResult` |
| `BookAdministrationRejection` | `DOC_02_Application.md` | `BookAdministrationRejection` |
| `PostEntryCommand` | `DOC_02_Application.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_Application.md` | `PostEntryResult` |
| `PostEntryResult.PreflightAccepted` | `DOC_02_Application.md` | `PostEntryResult.PreflightAccepted` |
| `PostEntryResult.Committed` | `DOC_02_Application.md` | `PostEntryResult.Committed` |
| `PostEntryResult.Rejected` | `DOC_02_Application.md` | `PostEntryResult.Rejected` |
| `MachineContract` | `DOC_02_Application.md` | `MachineContract` |
| `PostingApplicationService` | `DOC_02_Application.md` | `PostingApplicationService` |
| `PostingIdGenerator` | `DOC_02_Application.md` | `PostingIdGenerator` |
| `PostingRejection` | `DOC_02_Application.md` | `PostingRejection` |
| `PostingRejection.BookNotInitialized` | `DOC_02_Application.md` | `PostingRejection` |
| `PostingRejection.UnknownAccount` | `DOC_02_Application.md` | `PostingRejection` |
| `PostingRejection.InactiveAccount` | `DOC_02_Application.md` | `PostingRejection` |
| `PostingRejection.ReversalReasonForbidden` | `DOC_02_Application.md` | `PostingRejection.ReversalReasonForbidden` |
| `PostingRejection.ReversalReasonRequired` | `DOC_02_Application.md` | `PostingRejection.ReversalReasonRequired` |
| `PostingRejection.ReversalTargetNotFound` | `DOC_02_Application.md` | `PostingRejection.ReversalTargetNotFound` |
| `PostingRejection.DuplicateIdempotencyKey` | `DOC_02_Application.md` | `PostingRejection.DuplicateIdempotencyKey` |
| `PostingRejection.ReversalAlreadyExists` | `DOC_02_Application.md` | `PostingRejection.ReversalAlreadyExists` |
| `PostingRejection.ReversalDoesNotNegateTarget` | `DOC_02_Application.md` | `PostingRejection.ReversalDoesNotNegateTarget` |
| `UuidV7PostingIdGenerator` | `DOC_02_Application.md` | `UuidV7PostingIdGenerator` |
| `App` | `DOC_03_BookSessionsAndAdapters.md` | `App` |
| `BookSession` | `DOC_03_BookSessionsAndAdapters.md` | `BookSession` |
| `InMemoryBookSession` | `DOC_03_BookSessionsAndAdapters.md` | `InMemoryBookSession` |
| `PostingCommitResult` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingCommitResult.Committed` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult.Committed` |
| `PostingCommitResult.BookNotInitialized` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingCommitResult.UnknownAccount` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingCommitResult.InactiveAccount` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult` |
| `PostingCommitResult.DuplicateIdempotency` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult.DuplicateIdempotency` |
| `PostingCommitResult.DuplicateReversalTarget` | `DOC_03_BookSessionsAndAdapters.md` | `PostingCommitResult.DuplicateReversalTarget` |
| `PostingFact` | `DOC_03_BookSessionsAndAdapters.md` | `PostingFact` |
| `SqlitePostingFactStore` | `DOC_03_BookSessionsAndAdapters.md` | `SqlitePostingFactStore` |
