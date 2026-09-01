---
afad: "5.0.1"
version: "0.64.0"
domain: ADR_ACCRUAL_CUTOFFS
updated: "2026-09-01"
route:
  keywords: [fingrind, accrual cut-off, prepayment, accrued expense, deferred revenue, recognition schedule]
  questions: ["how does fingrind recognize a prepayment", "how does fingrind recognize deferred revenue", "what durable facts own an accrual cut-off"]
---

# Accrual Cut-offs ADR

**Purpose**: Define the accounting boundary that owns prepayments, accrued expenses, and deferred revenue in one protected book.
**Companion documents**:
- [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md)
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)

## Decision

Accrual Cut-offs is one supporting bookkeeping context for accrual-basis books. It owns the period-allocation facts that turn an initial prepayment or deferred-revenue balance into recognized expense or revenue, and the liability lifecycle of an accrued expense.

The context publishes accounting consequences into the existing protected-book ledger. It never keeps a second journal, balance, or report truth. Journal postings remain the book's durable accounting facts; cut-off state records why and how much of a temporary balance may be recognized or settled.

## Boundary And Language

Within this context:
- a **prepayment** is one cash-funded asset whose carrying amount is recognized as expense over its declared recognition interval;
- **deferred revenue** is one cash-funded liability whose carrying amount is recognized as revenue over its declared recognition interval;
- an **accrued expense** is one recognized expense with an outstanding liability that may later be settled;
- a **cut-off** is the identified business fact that owns one original amount, its accounts, and its permitted lifecycle applications;
- a **recognition** releases part of a prepayment or deferred-revenue carrying amount into its destination nominal account;
- a **settlement** reduces an accrued-expense liability through cash payment;
- a **recognition interval** is the inclusive date range in which a prepayment or deferred-revenue recognition may occur.

This context does not own invoice allocation, revenue-performance obligations, tax determination, payment allocation, fixed-asset depreciation, financing interest, realized foreign exchange, or payroll. Those terms retain their owners and must not be smuggled into cut-off commands.

## Aggregate And Invariants

One `AccrualCutoff` is the aggregate root. It is identified by a caller-supplied cut-off identifier and owns its original amount, account roles, lifecycle kind, recognition interval where applicable, and append-only applications.

The aggregate preserves these invariants:
- only an accrual-basis book admits a cut-off command;
- a prepayment uses a declared `PREPAID_EXPENSE` asset, an expense account, and cash;
- deferred revenue uses cash, a declared `DEFERRED_REVENUE` liability, and a revenue account;
- an accrued expense uses an expense account and a declared `ACCRUED_EXPENSE` liability;
- a recognition belongs only to a prepayment or deferred-revenue cut-off, falls within its recognition interval, and never exceeds the unrecognized amount;
- a settlement belongs only to an accrued-expense cut-off and never exceeds the unpaid liability;
- applications are append-only, retain their posting link, and cannot be backdated before their cut-off's permitted horizon;
- a compensating reversal records one append-only lifecycle fact, cannot predate the lifecycle horizon, and can reverse an origin only after all active applications have been reversed;
- every cut-off creation, recognition, and settlement is one typed posting and one atomic protected-book transaction.

The executor admission policy is the first owner of these rules. SQLite checks the durable shape, type-to-posting link, date bounds, signed application total, amount ceilings, and append-only records.

## Durable Facts

The protected book stores:
- one `accrual_cutoff` row per original cut-off, linked to its creation posting;
- one `accrual_cutoff_application` row per recognition, settlement, or compensating reversal, linked to its posting;
- no mutable recognized, settled, or remaining amount cache.

Remaining carrying amount and unpaid liability are derived from the original amount less the ordered applications. The aggregate therefore has one durable source of truth, and report projections cannot diverge from its lifecycle facts.

## Commands And Reports

The public typed write vocabulary is:
- `PREPAYMENT` to record the initial cash-funded asset and recognition interval;
- `DEFERRED_REVENUE` to record the initial cash-funded liability and recognition interval;
- `ACCRUED_EXPENSE` to recognize the expense and record its liability;
- `ACCRUAL_CUTOFF_RECOGNITION` to release an admitted amount from a prepayment or deferred-revenue cut-off;
- `ACCRUED_EXPENSE_SETTLEMENT` to pay an admitted amount of an accrued-expense liability.

Financial position and income statement projections include the resulting ledger postings. The context also publishes an accrual-cutoff schedule report so an operator can inspect each original amount, applications, and remaining carrying amount or unpaid liability.

## Context Map

Accrual Cut-offs consumes declared account taxonomy and the protected-book posting service as upstream published language. It supplies typed cut-off postings to the bookkeeping kernel. Account Registry remains the owner of account lifecycle; Tax, Foreign Exchange, Receivables and Payables, Fixed Assets, Financing, and Payroll remain separate contexts.

## Publication Evidence

This context is published in `0.61.0` with all of these owned artifacts present together:
- the typed commands and request contracts;
- executor admission and journal-resolution behavior;
- protected-book tables, foreign keys, checks, append-only triggers, and reconciliation;
- reporting and posting readback;
- rejection, lifecycle, persistence, cross-format, and end-to-end tests;
- synchronized public scope, request, response, and discovery documentation.

The published capability requires these owned artifacts to pass together; a partial request grammar or a ledger-only implementation is not publication.
