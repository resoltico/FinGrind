---
afad: "3.5"
version: "0.4.0"
domain: INDEX
updated: "2026-04-10"
route:
  keywords: [fingrind, index, routing, api, symbols, core, application, runtime, adapters]
  questions: ["where is the fingrind api documented", "which doc file covers SqlitePostingFactStore", "which doc file covers RequestProvenance"]
---

# API Index

**Purpose**: Routing table for the current public FinGrind API reference.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | accounting value objects, journal grammar, reversal linkage, request and committed provenance |
| `DOC_02_Application.md` | write-boundary commands, results, rejection taxonomy, posting service |
| `DOC_03_RuntimeAndAdapters.md` | runtime facts and stores, SQLite adapter, CLI entrypoint |

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AccountCode` | `DOC_01_Core.md` | `AccountCode` |
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
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `RequestProvenance` | `DOC_01_Core.md` | `RequestProvenance` |
| `SourceChannel` | `DOC_01_Core.md` | `SourceChannel` |
| `PostEntryCommand` | `DOC_02_Application.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_Application.md` | `PostEntryResult` |
| `PostEntryResult.PreflightAccepted` | `DOC_02_Application.md` | `PostEntryResult.PreflightAccepted` |
| `PostEntryResult.Committed` | `DOC_02_Application.md` | `PostEntryResult.Committed` |
| `PostEntryResult.Rejected` | `DOC_02_Application.md` | `PostEntryResult.Rejected` |
| `PostingApplicationService` | `DOC_02_Application.md` | `PostingApplicationService` |
| `PostingIdGenerator` | `DOC_02_Application.md` | `PostingIdGenerator` |
| `PostingRejection` | `DOC_02_Application.md` | `PostingRejection` |
| `PostingRejection.ReversalReasonForbidden` | `DOC_02_Application.md` | `PostingRejection.ReversalReasonForbidden` |
| `PostingRejection.ReversalReasonRequired` | `DOC_02_Application.md` | `PostingRejection.ReversalReasonRequired` |
| `PostingRejection.ReversalTargetNotFound` | `DOC_02_Application.md` | `PostingRejection.ReversalTargetNotFound` |
| `PostingRejection.DuplicateIdempotencyKey` | `DOC_02_Application.md` | `PostingRejection.DuplicateIdempotencyKey` |
| `PostingRejection.ReversalAlreadyExists` | `DOC_02_Application.md` | `PostingRejection.ReversalAlreadyExists` |
| `PostingRejection.ReversalDoesNotNegateTarget` | `DOC_02_Application.md` | `PostingRejection.ReversalDoesNotNegateTarget` |
| `App` | `DOC_03_RuntimeAndAdapters.md` | `App` |
| `InMemoryPostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `InMemoryPostingFactStore` |
| `PostingCommitResult` | `DOC_03_RuntimeAndAdapters.md` | `PostingCommitResult` |
| `PostingCommitResult.Committed` | `DOC_03_RuntimeAndAdapters.md` | `PostingCommitResult.Committed` |
| `PostingCommitResult.DuplicateIdempotency` | `DOC_03_RuntimeAndAdapters.md` | `PostingCommitResult.DuplicateIdempotency` |
| `PostingCommitResult.DuplicateReversalTarget` | `DOC_03_RuntimeAndAdapters.md` | `PostingCommitResult.DuplicateReversalTarget` |
| `PostingFact` | `DOC_03_RuntimeAndAdapters.md` | `PostingFact` |
| `PostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `PostingFactStore` |
| `SqlitePostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `SqlitePostingFactStore` |
