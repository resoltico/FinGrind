---
afad: "3.5"
version: "0.1.0"
domain: INDEX
updated: "2026-04-08"
route:
  keywords: [fingrind, index, routing, api, symbols, core, application, runtime, adapters]
  questions: ["where is the fingrind api documented", "which doc file covers SqlitePostingFactStore", "which doc file covers JournalEntry"]
---

# API Index

**Purpose**: Routing table for the current public FinGrind API reference.

## File Index

| File | Scope |
|:-----|:------|
| `DOC_01_Core.md` | accounting value objects, journal grammar, provenance enums |
| `DOC_02_Application.md` | write-boundary commands, results, service, rejection taxonomy |
| `DOC_03_RuntimeAndAdapters.md` | runtime facts and stores, SQLite adapter, CLI entrypoint |

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AccountCode` | `DOC_01_Core.md` | `AccountCode` |
| `CurrencyCode` | `DOC_01_Core.md` | `CurrencyCode` |
| `IdempotencyKey` | `DOC_01_Core.md` | `IdempotencyKey` |
| `PostingId` | `DOC_01_Core.md` | `PostingId` |
| `CorrectionReference` | `DOC_01_Core.md` | `CorrectionReference` |
| `CorrectionReference.CorrectionKind` | `DOC_01_Core.md` | `CorrectionReference.CorrectionKind` |
| `Money` | `DOC_01_Core.md` | `Money` |
| `JournalLine` | `DOC_01_Core.md` | `JournalLine` |
| `JournalLine.EntrySide` | `DOC_01_Core.md` | `JournalLine.EntrySide` |
| `JournalEntry` | `DOC_01_Core.md` | `JournalEntry` |
| `ProvenanceEnvelope` | `DOC_01_Core.md` | `ProvenanceEnvelope` |
| `ProvenanceEnvelope.ActorType` | `DOC_01_Core.md` | `ProvenanceEnvelope.ActorType` |
| `ProvenanceEnvelope.SourceChannel` | `DOC_01_Core.md` | `ProvenanceEnvelope.SourceChannel` |
| `PostEntryCommand` | `DOC_02_Application.md` | `PostEntryCommand` |
| `PostEntryResult` | `DOC_02_Application.md` | `PostEntryResult` |
| `PostEntryResult.PreflightAccepted` | `DOC_02_Application.md` | `PostEntryResult.PreflightAccepted` |
| `PostEntryResult.Committed` | `DOC_02_Application.md` | `PostEntryResult.Committed` |
| `PostEntryResult.Rejected` | `DOC_02_Application.md` | `PostEntryResult.Rejected` |
| `PostingRejectionCode` | `DOC_02_Application.md` | `PostingRejectionCode` |
| `PostingApplicationService` | `DOC_02_Application.md` | `PostingApplicationService` |
| `PostingFact` | `DOC_03_RuntimeAndAdapters.md` | `PostingFact` |
| `PostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `PostingFactStore` |
| `InMemoryPostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `InMemoryPostingFactStore` |
| `SqlitePostingFactStore` | `DOC_03_RuntimeAndAdapters.md` | `SqlitePostingFactStore` |
| `App` | `DOC_03_RuntimeAndAdapters.md` | `App` |
