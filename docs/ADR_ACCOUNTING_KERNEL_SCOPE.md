---
afad: "5.0.1"
version: "0.62.2"
domain: ADR_ACCOUNTING_KERNEL_SCOPE
updated: "2026-08-09"
route:
  keywords: [fingrind, bookkeeping kernel, country agnostic, functional currency, scope, internal management]
  questions: ["what bookkeeping kernel does fingrind publish today", "what does country agnostic mean in fingrind today", "what accounting scope is intentionally out of scope", "does fingrind publish a standards baseline"]
---

# Accounting Kernel Scope ADR

**Purpose**: Declare the exact executable bookkeeping kernel FinGrind publishes today, the public
truth boundaries for that kernel, and the adjacent accounting domains that remain outside the live
system.
**Companion documents**:
- [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DOC_01_Core.md](./DOC_01_Core.md)
- [DOC_01_Core_LedgerAndPosting.md](./DOC_01_Core_LedgerAndPosting.md)
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [USER_REQUESTS.md](./USER_REQUESTS.md)
- [USER_RESPONSES.md](./USER_RESPONSES.md)

## Decision

FinGrind publishes one executable bookkeeping kernel, not one public accounting-standards
baseline.

The machine contract, CLI help, examples, and docs must describe only this live kernel:
- one protected single-entity book
- one functional currency per book
- one country-agnostic built-in bookkeeping kernel plus separately bounded jurisdiction-owned contexts
- one business-event-first internal-management write surface plus one raw direct-journal path
- four built-in internal-management statements:
  - financial position
  - income statement
  - cash receipts and payments
  - changes in equity

Standards posture, jurisdiction overlays, and the route to a broader accounting foundation belong
to [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md), not to discovery facts that read
like current executable doctrine.

## Current Executable Kernel

<!-- BEGIN GENERATED CURRENT CAPABILITY SCOPE -->
The current kernel publishes these contract-owned capabilities:
- `business-event-posting` is `implemented`: Typed business events provide settled and on-credit sales and purchases, receipts and payments, inventory capitalization, relief, write-down, shrinkage, and count events, tax application through the declared selector, reversals, opening positions, owner contributions and withdrawals, interim result sweep, and fiscal-year close.
- `direct-journal` is `partial`: The raw DIRECT_JOURNAL path remains available for ordinary journals. Operative boundary: Direct journals remain available, but they cannot touch inventory accounts.
- `receivables-and-payables` is `partial`: On-credit sales and purchases plus receipts and payments are available. Operative boundary: On-credit sales and purchases plus receipts and payments are available only on accrual-basis books; invoice lifecycle and settlement allocation are excluded.
- `inventory` is `partial`: Inventory capitalization, relief, write-down, shrinkage, and count events are available. Operative boundary: Inventory is available only for the owner-managed trading template.
- `tax` is `partial`: Tax selection and obligation reporting are available. Operative boundary: Tax selection and obligation reporting are available; tax determination and filing doctrine are excluded.
- `foreign-exchange` is `partial`: Foreign-exchange facts may accompany eligible business events and direct journals, and typed foreign-currency receivable origination and settlement derive realized gain or loss while journal lines remain in the selected functional currency. Operative boundary: Only one foreign-currency receivable and one active settlement per retained obligation are available; rate sourcing, remeasurement, translation, hedging, and mixed-currency journal lines are excluded.
- `accrual-cutoffs` is `partial`: Accrual-basis prepayments, deferred revenue, accrued expenses, manual recognition or settlement applications, compensating reversals, and accrual-cutoff schedule reporting are available. Operative boundary: Applications are operator-authored exact amounts within each declared lifecycle; automatic allocation, tax and foreign-exchange composition, and revision-addressable report replay are excluded.
- `fixed-assets-and-depreciation` is `partial`: Fixed-asset capitalization, executor-resolved straight-line depreciation, disposal, compensating reversal, and fixed-asset register reporting are available. Operative boundary: The context uses a functional-currency cost model with one straight-line schedule per asset; leases, impairment, revaluation, tax depreciation, and statutory external reporting are excluded.
- `financing` is `partial`: Borrowing, principal repayment, interest accrual, interest payment, compensating reversal, and financing-register reporting are available. Operative boundary: The context records nominal principal and exact accrued interest only; leases, effective-interest amortization, fair-value measurement, covenants, tax withholding, and lender integrations are excluded.
- `latvian-monthly-payroll` is `partial`: One narrow Latvian 2026 monthly-employment payroll profile derives supported payroll accruals and their net-wage and state-remittance settlements, with payroll-register reporting. Operative boundary: Only the named EUR 2026 ordinary-employee profile is available; other worker profiles, periods, jurisdictions, legal-status determination, and statutory filing are excluded.
<!-- END GENERATED CURRENT CAPABILITY SCOPE -->

The current hard-break line also retains one SQLite protected book per accounting entity, explicit
initialization before postings or account declarations, one functional currency for every persisted
journal line, the opening-only `OPENING_POSITION` adoption path, close-owned movement for
`RESULT_HOLDING` and `RETAINED_ACCUMULATED`, comparative windows derived from the declared fiscal-year
anchor, and built-in reporting through financial position, income statement, cash receipts and payments,
changes in equity, tax obligations, and inventory valuation.

The current kernel therefore models one narrow internal-management bookkeeping system for a
single-entity, single-functional-currency book with owned foreign-exchange facts on eligible
events. It does not publish one standards-conformance baseline, one statutory reporting package,
or one general-purpose operating-accounting product.

## Public Truth Rules

The public contract may publish:
- executable book-model facts
- executable currency facts
- executable bookkeeping-kernel facts
- the built-in report inventory and per-report capability facts

The public contract must not publish:
- a "next target" as if it were current doctrine
- one standards-conformance posture as if it were executable accounting law
- one extension architecture that does not yet own multiple executable profiles
- policy-driven claims that the code does not actually enforce

## Context Publication Rule

An adjacent accounting domain is not published by a command name, a seeded account, or an aspirational catalog entry. Before it appears as an implemented or partial capability, one durable ADR must name its bounded context, ubiquitous language, aggregate and invariant owner, durable state, typed commands, reports touched, primary authority sources where it makes jurisdiction-sensitive claims, and its publication gate.

The context must then own those commands, state, storage constraints, read models, and end-to-end tests together. Each subsequent public context receives its own protocol release and an explicit protected-book-format decision; it must not become an incidental change inside another context's release. Until then, it remains excluded from discovery, request templates, help, reports, and the capability catalog.

## Capability Catalog

`CapabilityCatalog` is the canonical owner of the public scope statuses and operative boundaries
below. This bounded table is structurally checked by the build so the scope ADR cannot drift from
the contract owner.

<!-- BEGIN GENERATED CAPABILITY CATALOG -->
| Capability | Status | Published scope | Operative boundary |
|:-----------|:-------|:----------------|:-------------------|
| `business-event-posting` | `implemented` | Typed business events provide settled and on-credit sales and purchases, receipts and payments, inventory capitalization, relief, write-down, shrinkage, and count events, tax application through the declared selector, reversals, opening positions, owner contributions and withdrawals, interim result sweep, and fiscal-year close. |  |
| `direct-journal` | `partial` | The raw DIRECT_JOURNAL path remains available for ordinary journals. | Direct journals remain available, but they cannot touch inventory accounts. |
| `receivables-and-payables` | `partial` | On-credit sales and purchases plus receipts and payments are available. | On-credit sales and purchases plus receipts and payments are available only on accrual-basis books; invoice lifecycle and settlement allocation are excluded. |
| `inventory` | `partial` | Inventory capitalization, relief, write-down, shrinkage, and count events are available. | Inventory is available only for the owner-managed trading template. |
| `tax` | `partial` | Tax selection and obligation reporting are available. | Tax selection and obligation reporting are available; tax determination and filing doctrine are excluded. |
| `foreign-exchange` | `partial` | Foreign-exchange facts may accompany eligible business events and direct journals, and typed foreign-currency receivable origination and settlement derive realized gain or loss while journal lines remain in the selected functional currency. | Only one foreign-currency receivable and one active settlement per retained obligation are available; rate sourcing, remeasurement, translation, hedging, and mixed-currency journal lines are excluded. |
| `accrual-cutoffs` | `partial` | Accrual-basis prepayments, deferred revenue, accrued expenses, manual recognition or settlement applications, compensating reversals, and accrual-cutoff schedule reporting are available. | Applications are operator-authored exact amounts within each declared lifecycle; automatic allocation, tax and foreign-exchange composition, and revision-addressable report replay are excluded. |
| `fixed-assets-and-depreciation` | `partial` | Fixed-asset capitalization, executor-resolved straight-line depreciation, disposal, compensating reversal, and fixed-asset register reporting are available. | The context uses a functional-currency cost model with one straight-line schedule per asset; leases, impairment, revaluation, tax depreciation, and statutory external reporting are excluded. |
| `financing` | `partial` | Borrowing, principal repayment, interest accrual, interest payment, compensating reversal, and financing-register reporting are available. | The context records nominal principal and exact accrued interest only; leases, effective-interest amortization, fair-value measurement, covenants, tax withholding, and lender integrations are excluded. |
| `latvian-monthly-payroll` | `partial` | One narrow Latvian 2026 monthly-employment payroll profile derives supported payroll accruals and their net-wage and state-remittance settlements, with payroll-register reporting. | Only the named EUR 2026 ordinary-employee profile is available; other worker profiles, periods, jurisdictions, legal-status determination, and statutory filing are excluded. |
| `external-financial-reporting` | `excluded` | Standards-oriented external cash-flow presentation, OCI or comprehensive-income presentation, and note or disclosure packages are excluded. |  |
| `jurisdictional-bookkeeping-overlays` | `excluded` | Jurisdiction-specific chart templates, filing exports, and close doctrines are excluded. |  |
<!-- END GENERATED CAPABILITY CATALOG -->

## Intentional Exclusions

<!-- BEGIN GENERATED CAPABILITY EXCLUSIONS -->
The current kernel does not publish these first-class capabilities:
- `external-financial-reporting` is excluded: Standards-oriented external cash-flow presentation, OCI or comprehensive-income presentation, and note or disclosure packages are excluded.
- `jurisdictional-bookkeeping-overlays` is excluded: Jurisdiction-specific chart templates, filing exports, and close doctrines are excluded.
<!-- END GENERATED CAPABILITY EXCLUSIONS -->

Those domains remain outside the executable kernel until they own commands, state, storage, and
tests.

## Country-Agnostic Meaning

When FinGrind says "country agnostic" on the current kernel line, it means:
- the country-agnostic kernel avoids country-specific chart, tax, filing, and statement-layout rules
- the live kernel keeps one neutral built-in bookkeeping kernel rather than one jurisdictional
  doctrine
- a jurisdiction-owned context is published only when it names its authority sources and owns its
  executable commands, state, storage, and tests

It does not mean the repository currently ships one complete neutral accounting foundation or one
complete external-reporting product.

## Consequence

Because the live system is one bookkeeping kernel with separately bounded supporting contexts, not one standards baseline:
- discovery facts must name `bookkeepingKernel`, not `accountingBaseline`
- user and agent surfaces must describe unsupported accounting domains as unsupported, not as
  latent capability
- future accounting-foundation work must land as new executable contexts or hard breaks, not as
  descriptive contract inflation
