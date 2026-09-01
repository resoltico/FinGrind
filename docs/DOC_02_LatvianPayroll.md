---
afad: "5.0.1"
version: "0.64.0"
domain: LATVIAN_PAYROLL
updated: "2026-09-01"
scope:
  paths: [contract/src/main/java/dev/erst/fingrind/contract/payroll, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollBookkeepingEntryVariants.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/ResolvedLatvianPayrollSettlement.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollRegisterQuery.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollRegisterRow.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollRegisterReport.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollRegisterResult.java, contract/src/main/java/dev/erst/fingrind/contract/bookkeeping/LatvianPayrollSettlementStatus.java, contract/src/main/java/dev/erst/fingrind/contract/reportmodel/LatvianPayrollRegisterReportModelBuilder.java, contract/src/main/java/dev/erst/fingrind/contract/discovery/ContractLatvianPayrollTemplates.java, core/src/main/java/dev/erst/fingrind/core/TypedJournalSignatureCatalog.java, executor/src/main/java/dev/erst/fingrind/executor/ResolvedJournalSupport.java, executor/src/main/java/dev/erst/fingrind/executor/PostEntryLatvianPayrollRoleAccountSemantics.java, executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/LatvianPayrollAdmissionPolicy.java, executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/LatvianPayrollSettlementAdmissionPolicy.java, executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/LatvianPayrollRunRecord.java, executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/LatvianPayrollSettlementRecord.java, executor/src/main/java/dev/erst/fingrind/executor/bookkeeping/read/BookkeepingLatvianPayrollReadService.java, executor/src/main/java/dev/erst/fingrind/executor/spi/LatvianPayrollLookupStore.java]
  symbols: [LatvianMonthlyPayroll2026, LatvianMonthlyPayrollCalculation, LatvianPayrollRunId, LatvianPayrollEmployeeReference, LatvianPayrollMonth, LatvianPayrollSettlementKind, LatvianPayrollBookkeepingEntryVariants, ResolvedLatvianPayrollSettlement, TypedJournalSignatureCatalog, ResolvedJournalSupport, PostEntryLatvianPayrollRoleAccountSemantics, LatvianPayrollAdmissionPolicy, LatvianPayrollSettlementAdmissionPolicy, LatvianPayrollRunRecord, LatvianPayrollSettlementRecord, LatvianPayrollLookupStore, LatvianPayrollRegisterQuery, LatvianPayrollRegisterRow, LatvianPayrollRegisterReport, LatvianPayrollRegisterResult, LatvianPayrollSettlementStatus, LatvianPayrollRegisterReportModelBuilder, BookkeepingLatvianPayrollReadService, PostingLatvianPayrollRejectionSemantics, ContractLatvianPayrollTemplates]
route:
  keywords: [fingrind, Latvian payroll, 2026, monthly payroll, social insurance, salary tax, payroll register]
  questions: ["how does FinGrind calculate Latvian payroll", "which Latvian payroll cases are supported", "where are Latvian payroll legal sources"]
---

# Latvian Monthly Payroll API Reference

This file documents the payroll context described in [ADR_LATVIAN_PAYROLL.md](./ADR_LATVIAN_PAYROLL.md). It is not legal, tax, employment, or payroll advice. The supported profile is intentionally narrow; do not infer support for a worker or period that the command does not admit.

## Supported Profile

The calculation owner `LatvianMonthlyPayroll2026` admits only:
- EUR gross wages;
- a payroll month from `2026-01` through `2026-12`;
- one ordinary employee subject to the standard 2026 social-insurance split;
- an explicit `taxBookHeldAtEmployer: true` fact;
- an explicit `dependantCount: 0` fact;
- monthly gross wages no greater than EUR 8,775.00, so the context does not approximate the higher annual progressive rate.

It rejects, rather than approximates, pensioner and disability/service-pension treatment, dependants, foreign employment, multiple-employer treatment, benefits in kind, bonuses, leave, corrections, annual reconciliation, and all other periods.

## Exact Calculation

For the supported profile, all calculations are exact EUR-cent calculations with half-up rounding per component:

```text
employeeSocialContribution = roundHalfUp(grossWages × 10.50%)
employerSocialContribution = roundHalfUp(grossWages × 23.59%)
monthlyNonTaxableMinimum = min(EUR 550.00, grossWages − employeeSocialContribution)
personalIncomeTax = roundHalfUp((grossWages − employeeSocialContribution − monthlyNonTaxableMinimum) × 25.50%)
netWages = grossWages − employeeSocialContribution − personalIncomeTax
```

The output never accepts caller-supplied contribution, tax, or net-pay values. The executor resolves those values once from gross wages and stores the resolved calculation with the immutable payroll run.

## `LatvianMonthlyPayroll2026` And `LatvianMonthlyPayrollCalculation`

```java
public final class LatvianMonthlyPayroll2026
public record LatvianMonthlyPayrollCalculation(...)
```

## `LatvianPayrollWithholdingProfile`

`LatvianPayrollWithholdingProfile` is the narrow caller-attested withholding profile used to
establish whether the 2026 calculation is admissible.

```java
public record LatvianPayrollWithholdingProfile(boolean taxBookHeldAtEmployer, int dependantCount)
```

The published profile intentionally supports only a tax book held at the employer and no
dependants. It does not infer residency, employment status, or any other legal fact; those remain
outside the narrow profile and must be established against the authority sources below.

`LatvianMonthlyPayroll2026` is the single calculation owner for the named 2026 profile. It takes `LatvianPayrollMonth`, EUR `Money` gross wages, and `LatvianPayrollWithholdingProfile`, then returns the exact resolved social-contribution, non-taxable-minimum, personal-income-tax, net-wage, employer-cost, and state-remittance components. Its EUR-cent half-up component rounding is a deliberate FinGrind calculation convention for this bounded profile; it is not a substitute for verifying a worker's statutory treatment with the linked authority sources.

## `LatvianPayrollRunId`, `LatvianPayrollEmployeeReference`, And `LatvianPayrollMonth`

```java
public record LatvianPayrollRunId(String value)
public record LatvianPayrollEmployeeReference(String value)
public record LatvianPayrollMonth(YearMonth value)
```

`LatvianPayrollRunId` identifies an immutable run within a book. `LatvianPayrollEmployeeReference` is an opaque, lower-kebab operational reference and must not contain a worker name, national identifier, address, bank detail, or other personal data. `LatvianPayrollMonth` is the `YYYY-MM` payroll period and fixes the posting effective date to that month's final calendar day.

## `LatvianPayrollBookkeepingEntryVariants`, `LatvianPayrollAdmissionPolicy`, And `PostingLatvianPayrollRejectionSemantics`

```java
public sealed interface LatvianPayrollBookkeepingEntryVariants
public final class LatvianPayrollAdmissionPolicy
public record LatvianPayrollAdmissionPolicy.Resolution(...)
public final class PostingLatvianPayrollRejectionSemantics
```

`record-latvian-monthly-payroll` accepts opaque run and employee references, a payroll month, gross EUR wages, explicit `taxBookHeldAtEmployer` and `dependantCount` facts, six declared account roles, evidence, and provenance. The executor admits the profile, resolves the calculation, and builds the journal: debit wage expense and employer social-contribution expense; credit net-wages payable, employee social-contribution payable, employer social-contribution payable, and personal-income-tax payable. It rejects a non-EUR book, unsupported profile facts, a duplicate run id, or an already-active employee-month with the named `latvian-payroll-*` rejection vocabulary; it never falls back to a generic wage journal.

## `LatvianPayrollSettlementKind`, `ResolvedLatvianPayrollSettlement`, And `LatvianPayrollSettlementAdmissionPolicy`

```java
public enum LatvianPayrollSettlementKind
public record ResolvedLatvianPayrollSettlement(...)
public final class LatvianPayrollSettlementAdmissionPolicy
public record LatvianPayrollSettlementAdmissionPolicy.Resolution(...)
```

`record-latvian-payroll-net-wage-settlement` and `record-latvian-payroll-state-remittance` accept only a retained `payrollRunId`, an effective date, a cash account, evidence, and provenance. They never accept caller-authored payment amounts or liability accounts. `LatvianPayrollSettlementAdmissionPolicy` resolves the exact components from the immutable run: net wages for the employee payment, and employee social contribution, employer social contribution, and personal income tax for the state remittance. An active run may have only one active settlement of each kind; replacing a payment requires a compensating reversal. A settlement may not predate the payroll run, and a run may not be reversed until every active settlement is reversed.

`ResolvedLatvianPayrollSettlement` is the completed journal fact used by the executor. It preserves each component for readback and is not a caller input. The payroll settlement commands do not become generic `payment` commands because their liability accounts and amounts remain run-owned facts.

## `LatvianPayrollRunRecord`, `LatvianPayrollSettlementRecord`, And `LatvianPayrollLookupStore`

```java
public record LatvianPayrollRunRecord(...)
public record LatvianPayrollSettlementRecord(...)
public interface LatvianPayrollLookupStore
```

`LatvianPayrollRunRecord` is the durable, immutable record of the resolved origin facts and its linked posting. `LatvianPayrollSettlementRecord` is the durable lifecycle record for a net-wage or state-remittance posting and its compensating reversal, when present. `LatvianPayrollLookupStore` is the admission and readback boundary for run-id, active employee-month, settlement uniqueness, and reversal checks. The protected SQLite book independently checks the resolved formula, account-role mapping, linked posting origin, exact settlement journal, append-only rule, and active lifecycle so a direct write cannot forge a valid-looking payroll decomposition.

`PostEntryLatvianPayrollRoleAccountSemantics` owns the exact six-account role and taxonomy check before posting. `ResolvedJournalSupport` asserts the resulting typed economic-event class, while `TypedJournalSignatureCatalog` checks the context's stable journal anchor. These general ledger checks complement rather than replace the payroll role policy: the obligations are correctly ordinary `CURRENT_LIABILITY` accounts, and only the payroll policy can establish which four obligations belong to a payroll run.

## `LatvianPayrollRegisterQuery`, `LatvianPayrollRegisterReport`, And `BookkeepingLatvianPayrollReadService`

```java
public record LatvianPayrollRegisterQuery()
public record LatvianPayrollRegisterRow(...)
public record LatvianPayrollRegisterReport(...)
public sealed interface LatvianPayrollRegisterResult
public record LatvianPayrollSettlementStatus(...)
public final class LatvianPayrollRegisterReportModelBuilder
public final class BookkeepingLatvianPayrollReadService
```

`latvian-payroll-register` is the complete book-wide read model for this context. It returns each retained run exactly once, including reversed runs, its immutable calculated components, and every retained net-wage or state-remittance settlement with its own reversal lineage. A run without a settlement remains visible as `unsettled`; a compensating reversal never erases the earlier run or settlement. The command's text, JSON, CSV, and PDF projections all derive from `LatvianPayrollRegisterReportModelBuilder`, so CSV has one tabular record per run-and-settlement lineage item rather than a cell-per-line encoding.

The register is an operational reconciliation aid, not a statutory filing. Compare it with the current evidence retained for the payroll period and the primary authority sources below; FinGrind does not submit EDS reports or determine a worker's legal status.

## `ContractLatvianPayrollTemplates` And Its Request Descriptors

```java
public interface ContractLatvianPayrollTemplates
public record ContractLatvianPayrollTemplates.MonthlyPayrollTemplateDescriptor(...)
public record ContractLatvianPayrollTemplates.PayrollSettlementTemplateDescriptor(...)
```

`ContractLatvianPayrollTemplates` owns the public machine-template types for this bounded context. `MonthlyPayrollTemplateDescriptor` is the top-level `print-request-template record-latvian-monthly-payroll` scaffold, not a nested template-only grammar. Its required fields are `payrollRunId`, `employeeReference`, `payrollMonth`, `taxBookHeldAtEmployer`, `dependantCount`, the six account-code fields, and `grossWages`; its accepted evidence source-document types are `payroll-register`, `employment-contract`, and `timesheet`. Replace every placeholder with retained evidence and operational facts before posting. The scaffold deliberately omits caller-authored contribution, tax, and net-wage amounts because those are executor-resolved facts.

`PayrollSettlementTemplateDescriptor` is the `payrollRunId` block in the two settlement scaffolds. Net-wage settlements accept `payroll-register` or `bank-payment-order` evidence; state remittances accept `social-insurance-report` or `bank-payment-order` evidence. Both templates deliberately omit payment amounts and payable-account fields because the retained payroll run is their sole source.

## Authority Sources

The calculation constants were verified on 2026-07-15 against:
- [State Revenue Service: Mandatory State Social Insurance Contributions](https://www.vid.gov.lv/en/mandatory-state-social-insurance-contributions), which states the standard employee split as 23.59% employer and 10.50% employee;
- [State Revenue Service: Personal Income Tax](https://www.vid.gov.lv/en/personal-income-tax), which publishes the 2026 EUR 550 monthly non-taxable minimum and its conditions;
- [State Revenue Service: Personal Income Tax Rates](https://www.vid.gov.lv/en/personal-income-tax-rates), which publishes the applicable rate and progressive threshold guidance;
- [State Revenue Service: 2026 non-taxable minimum](https://www.vid.gov.lv/lv/neapliekamais-minimums), which documents the authority's payroll-tax-book and dependant inputs for the supported deduction example;
- [Likumi.lv: Darba likums](https://likumi.lv/ta/id/26019-darba-likums);
- [Likumi.lv: Par valsts sociālo apdrošināšanu](https://likumi.lv/ta/id/45466);
- [Likumi.lv: Par iedzīvotāju ienākuma nodokli](https://likumi.lv/ta/id/56880-par-iedzivotaju-ienakuma-nodokli); and
- [Likumi.lv: Cabinet Regulation No. 827](https://likumi.lv/ta/id/217642-noteikumi-par-valsts-socialas-apdrosinasanas-obligato-iemaksu-veiceju-registraciju-un-zinojumiem-par-valsts-socialas-apdrosinas).

The Likumi.lv links are the current Latvian-language consolidated texts. Do not substitute an English translation for them when checking a legal obligation, because translations can lag amendments. Use [DOC_00_PrimarySources.md](./DOC_00_PrimarySources.md) to recheck the current source material before each payroll period or statutory filing. The context does not generate an EDS report, employee registration notice, or statutory payslip.

## Example

For EUR 2,000.00 of supported gross wages in a supported 2026 payroll month:

```text
employee social contribution  EUR 210.00
employer social contribution  EUR 471.80
monthly non-taxable minimum   EUR 550.00
personal income tax           EUR 316.20
net wages                     EUR 1,473.80
total employer cost           EUR 2,471.80
state remittance              EUR 998.00
```

This is a worked calculation for the named supported profile, not a claim that every Latvian employee has that treatment.
