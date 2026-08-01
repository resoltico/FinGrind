---
afad: "5.0.1"
version: "0.62.0"
domain: INDEX
updated: "2026-07-30"
route:
  keywords: [fingrind, bookkeeping, read model, account ledger, ledger pagination, keyset cursor, report]
  questions: ["where is account-ledger pagination documented", "which types own the account-ledger cursor", "how does the account ledger continue across pages"]
---

# Bookkeeping Read Symbol Routing

**Purpose**: Route account-ledger pagination and running-balance symbols to their public and local ownership boundaries.

## Symbol Routing

| Symbol | File | Section |
|:-------|:-----|:--------|
| `AccountLedgerEntry` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerPageCursor` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerPagination` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerQuery` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerReport` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerResult` | `DOC_02_AdministrationAndReports.md` | `AccountLedgerPageCursor`, `AccountLedgerPagination`, `AccountLedgerQuery`, `AccountLedgerEntry`, `AccountLedgerReport`, And `AccountLedgerResult` |
| `AccountLedgerCursor` | `DOC_03_BookSessionsAndAdapters.md` | `AccountLedgerCursor` |
