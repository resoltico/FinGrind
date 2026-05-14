---
afad: "4.0"
version: "0.37.0"
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

FinGrind does not currently claim:
- full IFRS compliance
- IFRS for SMEs parity
- one complete statutory bookkeeping-and-reporting product for every entity shape

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
- opening adoption balances are represented through `OPENING_BALANCE` postings and are accepted
  only before the first committed posting enters the book, which makes the opening statement a
  one-time seed boundary rather than one ongoing posting family
- generated period-close postings are separate from caller-authored postings
- comparative reporting windows and comparative statement payloads are derived from the declared
  fiscal-year anchor through the built-in statement-comparative policy seam rather than by blind
  calendar subtraction

This means FinGrind currently models one clean bookkeeping kernel for:
- sole traders and small organizations that keep one functional-currency book
- exact money, append-only postings, close-period workflow, and core internal statements

It intentionally does not yet model the broader external-reporting and multi-currency layers that
standards such as IAS 7 and IAS 21 require beyond that bookkeeping kernel.
It also does not yet model the broader SME-operating subdomains that real businesses need above a
ledger, such as invoices, receivables, payables, tax determination, inventory, payroll, or group
reporting.

More specifically on the current kernel line:
- built-in reporting stops at financial position, income statement, and changes in equity; cash
  flows, OCI/comprehensive-income reporting, and note/disclosure packages belong to adjacent
  reporting contexts
- the chart of accounts is flat; no parent-child hierarchy or first-class report-taxonomy
  structure is built into the kernel account model
- tax is not a first-class domain; users may post tax-bearing amounts manually, but tax
  registrations, tax codes, rate schedules, recoverability, inclusivity, determination rules, and
  filing obligations are not modeled yet
- group reporting, consolidation, and intercompany elimination are not modeled in the current
  single-entity kernel

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
- hierarchical chart and reporting taxonomy
- invoicing / receivables / payables operational contexts
- tax determination and filing contexts
- group reporting and consolidation
- jurisdiction-specific close/reporting rules
- statutory chart templates or filing exports

Those extensions must preserve the current core invariants rather than weakening them with
compatibility shims.

The built-in policy pack already owns:
- fiscal-year-aware comparative reporting windows and comparative payload data
- the named comparative-reporting seam that future jurisdiction-specific packs may override
