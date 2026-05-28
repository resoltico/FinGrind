---
afad: "4.0"
version: "0.49.0"
domain: ADR_ACCOUNTING_KERNEL_SCOPE
updated: "2026-05-28"
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
- [DOC_02_ProtocolAndDiscovery.md](./DOC_02_ProtocolAndDiscovery.md)
- [USER_REQUESTS.md](./USER_REQUESTS.md)

## Decision

FinGrind publishes one executable bookkeeping kernel, not one public accounting-standards
baseline.

The machine contract, CLI help, examples, and docs must describe only this live kernel:
- one protected single-entity book
- one functional currency per book
- one built-in bookkeeping kernel
- one cash-oriented internal-management write surface
- three built-in internal-management statements:
  - financial position
  - income statement
  - changes in equity

Standards posture, jurisdiction overlays, and the route to a broader accounting foundation belong
to [ADR_ACCOUNTING_FOUNDATION.md](./ADR_ACCOUNTING_FOUNDATION.md), not to discovery facts that read
like current executable doctrine.

## Current Executable Kernel

The current hard-break line is:
- one SQLite protected book equals one accounting entity book
- books are explicitly initialized before postings or account declarations
- every caller-authored posting line and every persisted journal line must use the selected book
  functional currency
- mixed-currency entries are rejected
- built-in typed entry support is limited to cash revenue, cash expense, equity contribution, and
  equity withdrawal
- one explicit administrative journal-adjustment path remains available for openings and
  corrections that the current typed cash-entry family does not own
- generated period-result-transfer postings remain separate from caller-authored postings
- comparative windows derive from the declared fiscal-year anchor
- built-in reporting stops at financial position, income statement, and changes in equity

The current kernel therefore models one narrow internal-management bookkeeping system for a
single-entity, single-functional-currency book. It does not publish one standards-conformance
baseline, one statutory reporting package, or one general-purpose operating-accounting product.

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

## Intentional Exclusions

The current kernel does not publish first-class support for:
- cash-flow reporting
- OCI / comprehensive-income presentation
- note or disclosure packages
- tax determination or filing doctrine
- foreign-exchange transaction, settlement, or remeasurement doctrine
- receivables, payables, invoicing, settlement, inventory, payroll, or other operational
  subledgers
- jurisdiction-specific chart templates, filing exports, or close doctrines

Those domains remain outside the executable kernel until they own commands, state, storage, and
tests.

## Country-Agnostic Meaning

When FinGrind says "country agnostic" on the current kernel line, it means:
- the live kernel avoids country-specific chart, tax, filing, and statement-layout rules
- the live kernel keeps one neutral built-in bookkeeping kernel rather than one jurisdictional
  doctrine
- future jurisdiction or standards overlays must arrive as real executable owners, not prose-only
  aspirations

It does not mean the repository currently ships one complete neutral accounting foundation or one
complete external-reporting product.

## Consequence

Because the live system is one bookkeeping kernel, not one standards baseline:
- discovery facts must name `bookkeepingKernel`, not `accountingBaseline`
- user and agent surfaces must describe unsupported accounting domains as unsupported, not as
  latent capability
- future accounting-foundation work must land as new executable contexts or hard breaks, not as
  descriptive contract inflation
