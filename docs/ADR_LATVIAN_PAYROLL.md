---
afad: "5.0.1"
version: "0.62.0"
domain: ADR_LATVIAN_PAYROLL
updated: "2026-07-30"
route:
  keywords: [fingrind, Latvian payroll, monthly payroll, payroll run, net wages, social insurance, personal income tax]
  questions: ["what Latvian payroll does FinGrind support", "how is a Latvian monthly payroll run calculated", "what payroll cases does FinGrind reject"]
---

# Latvian Monthly Payroll ADR

**Purpose**: Define the first jurisdiction-owned payroll boundary: auditable Latvian monthly employment payroll for a deliberately narrow, named worker and period profile.
**Companion documents**:
- [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md)
- [ADR_ACCOUNTING_KERNEL_SCOPE.md](./ADR_ACCOUNTING_KERNEL_SCOPE.md)
- [DEVELOPER_DOMAIN_MODEL.md](./DEVELOPER_DOMAIN_MODEL.md)
- [DOC_02_PostingAndLedgerPlans.md](./DOC_02_PostingAndLedgerPlans.md)

## Decision

Latvian Monthly Payroll is a supporting bounded context. It owns the calculation and accounting representation of one supported employee-month payroll run. It does not make the jurisdiction-neutral bookkeeping kernel statutory by implication.

The first release profile is restricted to a 2026 EUR monthly payroll for one ordinary Latvian employee who is insured for all standard mandatory social-insurance types. The request explicitly states `taxBookHeldAtEmployer: true` and `dependantCount: 0`; those are supported profile facts, not defaults. Requests outside that profile are rejected. They are not routed through a generic wage journal and are not approximated.

## Boundary And Language

Within this context:
- a **payroll run** is one immutable calculation for one opaque employee reference and one payroll month;
- **gross wages** are the EUR remuneration before employee withholdings;
- **employee social contribution**, **employer social contribution**, and **withheld personal income tax** are the separately calculated statutory components;
- **monthly non-taxable minimum** is the supported employee's fixed monthly deduction in the supported period;
- **withholding profile** is the explicit request pair `taxBookHeldAtEmployer` and `dependantCount` used to admit the supported statutory calculation;
- **net wages payable** are gross wages less the employee social contribution and withheld personal income tax;
- **state remittance payable** is the employee social contribution, employer social contribution, and withheld personal income tax owed to the state;
- an **employee reference** is an opaque operational identifier. It is not a name, national identifier, bank account, address, or other personal data.

The context does not own employment contracts, attendance, leave, overtime, benefits in kind, pension or disability treatment, dependants, annual progressive-tax reconciliation, foreign employment, corrections, EDS filings, or employee payment instructions. Those facts must not be inferred from a gross-wages number.

## Aggregate And Invariants

One `LatvianPayrollRun` is the aggregate root. It is identified by a caller-supplied payroll-run identifier and has one opaque employee reference and one payroll month.

The aggregate preserves these invariants:
- only EUR payroll months in calendar year 2026 are admitted;
- `taxBookHeldAtEmployer` must be true and `dependantCount` must be zero; neither fact is inferred from gross wages, the selected accounts, or the employee reference;
- an employee reference has at most one payroll run for a payroll month;
- the employee and employer social-contribution components, non-taxable minimum, personal-income-tax withholding, net wages, and state-remittance total are computed by the executor from the supported statutory profile;
- user input never supplies a tax, contribution, or net-pay amount;
- the payable accounts are explicit, declared liabilities and remain distinct from the wage and employer-contribution expense accounts;
- the accepted journal exactly equals the resolved component calculation;
- a payroll run, its linked posting, and any owned settlement facts are append-only and cannot be edited into another employee, month, amount, or account mapping;
- a compensating reversal retains the original run's resolved components and writes a compensating lifecycle fact rather than recomputing historical law.

The executor is the first owner of profile admission, calculation, account-role checks, duplicate prevention, and lifecycle resolution. SQLite stores the immutable run and linked lifecycle facts, constrains their shape and posting links, and rejects update or delete attempts.

## Durable Facts

The protected book stores one payroll-run origin linked to its posting, explicit admitted withholding-profile facts, and its exact resolved monetary components. It stores no worker identity beyond the opaque employee reference and no mutable net-pay cache.

The payroll register is derived from durable payroll-run and settlement facts plus the immutable linked postings. The general ledger remains the accounting source of truth; the payroll run explains the statutory decomposition that the journal alone cannot safely reconstruct.

## Primary Sources And Parameter Custody

The executable 2026 calculation parameters are versioned in code and documented with retrieval date in [DOC_02_LatvianPayroll.md](./DOC_02_LatvianPayroll.md). Their authority sources are the Latvian legislation and State Revenue Service links in [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md).

The State Revenue Service publishes the standard employee social-insurance split as 23.59% employer and 10.50% employee, and the authority's personal-income-tax guidance publishes the 2026 monthly non-taxable minimum and rate information. The context uses those values only for its stated profile. It must be revised or withdrawn when the authoritative sources change.

## Context Map

Latvian Monthly Payroll consumes the protected-book posting service and Account Registry's declared account taxonomy. It supplies resolved payroll postings and a payroll register to the bookkeeping kernel. It does not absorb Tax Registration, generic payment allocation, employment records, or statutory filing into its model.

## Commands And Reports

The public typed write vocabulary is:
- `record-latvian-monthly-payroll`, which resolves and records one supported payroll accrual from gross EUR wages and retained payroll facts;
- `record-latvian-payroll-net-wage-settlement`, which discharges only the retained net-wages obligation for one payroll run;
- `record-latvian-payroll-state-remittance`, which discharges only the retained employee social-contribution, employer social-contribution, and personal-income-tax obligations for one payroll run; and
- `record-reversal`, which retains a compensating payroll-run or settlement lifecycle fact instead of replacing historical calculation or settlement facts.

`latvian-payroll-register` is the dedicated operational reconciliation report. It publishes each retained run, resolved statutory components, settlement state, and compensating-reversal lineage. General-ledger queries and built-in financial statements expose the resulting journal postings under their own report contexts; they do not calculate, allocate, or reconcile payroll facts.

## Publication Evidence

The context is published in `0.61.0` with all of these owned artifacts present together:
- typed commands and request contracts that expose only supported facts;
- executor-owned statutory resolution and deterministic rejection of unsupported profiles;
- protected-book tables, foreign keys, checks, append-only triggers, and reconciliation;
- payroll register and posting readback;
- calculation, rejection, persistence, lifecycle, cross-format, and end-to-end tests;
- synchronized primary-source, request, response, discovery, example, and scope documentation.

Publishing a calculator, an unlinked wage journal, or a statutory-looking example without all these owners is not payroll support.
