---
afad: "4.0"
version: "0.59.0"
domain: CONTRACT_REPORT_MODEL
updated: "2026-07-04"
route:
  keywords: [fingrind, report-model, shared-report, report-builder, json, csv, pdf, text, projection, report-context]
  questions: ["where is the shared report model documented in fingrind", "which doc covers ReportModel and ReportSection", "where are the report model builders documented"]
---

# Shared Report Model Reference

This file documents the exported shared report-model package that every public report surface now
publishes before JSON, text, CSV, and PDF projection.

## `ReportModel`, `ReportSection`, `ReportColumn`, `ReportRow`, `ReportTotals`, `ReportVerdict`, `ReportContext`, `ReportModelBuilder`, `ReportColumn.Alignment`, And `ReportModel.Orientation`

These shared report-model types own the projection-neutral report content spine used by every
public report surface.

```java
public record ReportModel(...)
public record ReportSection(...)
public record ReportColumn(...)
public record ReportRow(...)
public record ReportTotals(...)
public record ReportVerdict(...)
public record ReportContext(...)
public interface ReportModelBuilder<T>
```

- Purpose: keep one canonical report content model across JSON, text, CSV, and PDF projection
  modes
- Context semantics: `ReportContext` is mandatory on every report and carries entity, doctrine,
  currency, fiscal-year, temporal-scope, and tax-context facts in one stable home
- Projection semantics: sections carry summary verdicts, tabular rows, and optional totals blocks,
  while `ReportColumn.Alignment` and `ReportModel.Orientation` expose formatter hints only

## `AccountBalanceReportModelBuilder`, `TrialBalanceReportModelBuilder`, `AccountLedgerReportModelBuilder`, `PeriodSummaryReportModelBuilder`, `FinancialPositionReportModelBuilder`, `IncomeStatementReportModelBuilder`, `CashFlowStatementReportModelBuilder`, `ChangesInEquityReportModelBuilder`, And `TaxObligationReportModelBuilder`

These builders own the one-way translation from family-specific reported results into the shared
report model.

```java
public final class AccountBalanceReportModelBuilder
public final class TrialBalanceReportModelBuilder
public final class AccountLedgerReportModelBuilder
public final class PeriodSummaryReportModelBuilder
public final class FinancialPositionReportModelBuilder
public final class IncomeStatementReportModelBuilder
public final class CashFlowStatementReportModelBuilder
public final class ChangesInEquityReportModelBuilder
public final class TaxObligationReportModelBuilder
```

- Purpose: keep report-family semantics local while projecting one shared content spine outward
- Family coverage: account balance, trial balance, account ledger, period summary, financial
  position, income statement, cash receipts and payments, changes in equity, and tax obligation
