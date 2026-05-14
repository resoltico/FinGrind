---
afad: "4.0"
version: "0.36.0"
domain: ADR_ACCOUNTING_BASELINE
updated: "2026-05-14"
route:
  keywords: [fingrind, accounting baseline, ifrs, country agnostic, functional currency, scope, external reporting]
  questions: ["what accounting standards baseline does fingrind target", "is fingrind full ifrs", "what does legislation agnostic mean in fingrind", "what accounting scope is intentionally out of scope"]
---

# Accounting Baseline ADR

**Purpose**: Declare the accounting-standards baseline that informs FinGrind's current design and
its intentional exclusions.
**Companion documents**:
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_02_AdministrationAndReports.md](./DOC_02_AdministrationAndReports.md)
- [USER_REQUESTS.md](./USER_REQUESTS.md)

## Decision

FinGrind's current target is a country-agnostic bookkeeping core, not a full jurisdictional
reporting package and not a full external IFRS or local-GAAP compliance engine.

The present baseline is:
- general-purpose financial-reporting concepts from the IFRS Conceptual Framework
- a functional-currency anchor in the sense used by IAS 21
- a double-entry ledger whose current built-in primary statements are financial position, income
  statement, and changes in equity

FinGrind does not currently claim built-in support for:
- a full statement-of-cash-flows model
- OCI / comprehensive-income presentation
- external note and disclosure packages
- tax, jurisdiction-specific filing, or local statutory presentation rules
- foreign-currency translation, remeasurement, or FX gain/loss accounting inside one book

## Current Product Boundary

On the current hard-break line:
- one protected book belongs to one accounting entity
- one book has one declared functional currency
- every caller-authored posting and every persisted journal line must use that book functional
  currency
- mixed-currency journal entries are rejected
- opening adoption balances are represented through `OPENING_BALANCE` postings
- generated period-close postings are separate from caller-authored postings

This means FinGrind currently models one clean bookkeeping kernel for:
- sole traders and small organizations that keep one functional-currency book
- exact money, append-only postings, close-period workflow, and core internal statements

It intentionally does not yet model the broader external-reporting and multi-currency layers that
standards such as IAS 7 and IAS 21 require beyond that bookkeeping kernel.

## Consequence

When the repository says "legislation agnostic", it means:
- the current core avoids country-specific chart rules, filing rules, tax rules, and presentation
  layouts
- future jurisdictional or standards-specific layers should arrive as extensions on top of the
  current bookkeeping kernel

It does not mean that the current repository already ships a complete country-agnostic IFRS
reporting package.

## Extension Rule

Future extensions may add:
- statement of cash flows
- OCI / comprehensive-income layers
- FX translation and exchange-difference accounting
- jurisdiction-specific close/reporting rules
- statutory chart templates or filing exports

Those extensions must preserve the current core invariants rather than weakening them with
compatibility shims.
