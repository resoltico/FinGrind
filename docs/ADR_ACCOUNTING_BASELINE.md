---
afad: "4.0"
version: "0.43.0"
domain: ADR_ACCOUNTING_BASELINE
updated: "2026-05-20"
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

FinGrind's current accounting-baseline target is `INTERNAL_MANAGEMENT_STATEMENTS`.
That target means one country-neutral, policy-driven bookkeeping foundation for one protected
single-entity book, not one full jurisdictional reporting package and not one full IFRS or
local-GAAP compliance engine.

The built-in default policy pack is the neutral single-entity policy pack. It owns:
- the current accounting-basis vocabulary for book creation
- the current parent-child chart structure and statement-line taxonomy contract
- the current entity-form-aware close doctrine
- the current built-in statement-presentation taxonomy posture
- fiscal-year-aware comparative reporting windows
- the published neutral accounting-policy posture for the current kernel
- the declared adjacent-context boundary for tax, FX, richer reporting, operational subledgers,
  organization graphs, and source evidence

The present baseline is:
- general-purpose financial-reporting concepts from the IFRS Conceptual Framework
- a functional-currency anchor in the sense used by IAS 21
- a double-entry ledger whose current built-in primary statements are financial position, income
  statement, and changes in equity

FinGrind does not currently claim:
- full IFRS compliance
- IFRS for SMEs parity
- one complete statutory bookkeeping-and-reporting product for every entity shape

The next declared baseline target is `BASIC_STANDARD_REPORTING_FOUNDATION`.
FinGrind may only move to that target once the kernel and adjacent contexts gain:
- source-document and approval evidence
- typed business-event commands and posting recipes
- first-class cash-flow and disclosure reporting support
- tax and foreign-exchange foundation models

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
- declared accounts carry explicit parent-child hierarchy and typed statement-line taxonomy while
  account-code text remains an opaque book-local identifier
- period close is selected through an entity-form-aware closing-equity classification policy
- tax is not a first-class domain; users may post tax-bearing amounts manually, but tax
  registrations, tax codes, rate schedules, recoverability, inclusivity, determination rules, and
  filing obligations are not modeled yet
- group reporting, consolidation, and intercompany elimination are not modeled in the current
  single-entity kernel

## Consequence

When the repository says "legislation agnostic", it means:
- the current core avoids country-specific chart rules, filing rules, tax rules, and presentation
  layouts
- the current kernel publishes a neutral accounting-policy pack rather than one country doctrine
- future jurisdictional or standards-specific layers must arrive through explicit policy seams or
  adjacent bounded contexts on top of the current bookkeeping kernel

It does not mean that the current repository already ships a complete country-agnostic IFRS
reporting package.

## Extension Rule

Future extensions may add:
- statement of cash flows
- OCI / comprehensive-income layers
- FX translation and exchange-difference accounting
- richer statutory and disclosure presentation taxonomy
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
- the declared neutral accounting-basis vocabulary for current book creation
- executable chart-taxonomy policy for hierarchy and statement-line classification
- executable entity-form-aware close policy for the active closing-equity classification
- executable built-in statement-presentation policy for current internal statements

The built-in kernel intentionally publishes no first-class tax, foreign-exchange, or
source-evidence policy seams until those domains own executable commands, state, storage, and
tests.
