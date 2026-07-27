---
afad: "5.0.1"
version: "0.61.0"
domain: CONTRACT_REPORT_MODEL
updated: "2026-07-24"
scope:
  paths: ["contract/src/main/java/dev/erst/fingrind/contract/reportmodel"]
  symbols: ["ReportModel", "ReportCsvProjection", "InventoryValuationReportModelBuilder"]
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

## `ReportCsvProjection`

`ReportCsvProjection` carries the validated tabular CSV headers and exact rows owned by one
`ReportModel` when that report has a specialized CSV record family.

```java
public record ReportCsvProjection(List<String> headers, List<List<String>> rows)
```

- Invariant: headers are non-empty and unique, and every row has exactly one cell per header
- Ownership: the family-specific report-model builder creates the projection; CLI CSV rendering
  consumes it directly
- Compatibility: internal projection data excluded from the public JSON report shape

## `BookQueryReportResult`

`BookQueryReportResult<REPORTED>` is the uniform public grammar for book-query report results:
`reported()` returns the family-specific report on success and `rejection()` returns the
deterministic `BookQueryRejection` otherwise. It lets shared output infrastructure preserve the
closed success-or-rejection semantics without erasing the report family.

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
- Account-ledger attestation semantics: each ledger row retains its attestation order as a compact
  inline reference. Text and PDF do not add a detached commitment lookup section or reproduce
  complete operation heads; JSON and CSV retain each row's exact operation head. This keeps the
  ledger table readable without losing the machine-verifiable link.

## `InventoryValuationReportModelBuilder`

`InventoryValuationReportModelBuilder` projects exact inventory-pool valuation into the shared
report model used by JSON, text, CSV, and PDF output.

```java
public final class InventoryValuationReportModelBuilder
```

- It labels `roundedMovingAverageUnitCostProjection` informational and keeps exact carrying value
  separate from that rounded display projection
- When movement detail is requested, it adds ordered durable movement sections without creating a
  second report model
- It owns the exact tabular CSV projection used by the production CSV renderer, so CSV, JSON,
  text, and PDF share one valuation model rather than independently rebuilding valuation facts
