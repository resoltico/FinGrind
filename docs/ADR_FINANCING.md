---
afad: "5.0.1"
version: "0.62.2"
domain: ADR_FINANCING
updated: "2026-08-09"
route:
  keywords: [fingrind, financing, borrowing, principal repayment, interest accrual, financing register]
  questions: ["how does fingrind record a borrowing", "how does interest accrue in fingrind", "what owns a financing arrangement"]
---

# ADR: Financing

## Status

Published in `0.61.0`. Borrowing, principal repayment, interest accrual, interest payment, compensating reversals, and financing-register reporting are one public capability with one owned lifecycle boundary.

The release boundary records nominal principal and exact accrued interest. Leases, effective-interest amortization, fair-value measurement, covenant monitoring, tax withholding, and lender integrations remain outside this context.

## Boundary And Language

The Financing context owns a **financing arrangement**, **borrowing**, **principal outstanding**, **interest accrual**, **interest payable**, **interest payment**, and **principal repayment**. It does not own leases, amortized-cost effective-interest calculations, fair-value measurement, covenant monitoring, tax withholding, or lender integrations.

Its aggregate is one `FinancingArrangement`, identified by `FinancingArrangementId`. The executor admits applications against immutable arrangement facts, derives the relevant liability account, and preserves principal and accrued-interest bounds. SQLite owns immutable arrangement and application facts plus compensating-reversal lineage.

This small bookkeeping model is not a claim of IFRS 9 compliance. [IFRS 9](https://www.ifrs.org/issued-standards/list-of-standards/ifrs-9-financial-instruments/) is the primary reference for the broader financial-instruments domain that remains outside this boundary.

## Commands And Reports

The typed commands are `record-financing-borrowing`, `record-financing-principal-repayment`, `record-financing-interest-accrual`, and `record-financing-interest-payment`. The dedicated `financing-register` reconciles original principal, principal outstanding, accrued interest, and settlement history to the general ledger.

## Invariants

- A repayment never exceeds principal outstanding.
- An interest payment never exceeds accrued unpaid interest.
- Applications do not precede their arrangement and use its currency.
- Reversals are explicit compensating lifecycle facts; no arrangement or application mutates in place.

## Publication Condition

Publication requires typed commands, durable reversal-aware storage that binds lifecycle amounts to immutable posting facts, executor admission and resolution, request contracts and templates, register reporting, end-to-end tests, and protected-book format `57`. Non-current book formats, whether older or newer, are rejected rather than upgraded in place. The release-boundary contract test verifies this condition against the public operation registry and this ADR.
