package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementGrossProfitSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementPresentationSupport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.tax.TaxObligationReport;
import java.util.List;
import java.util.stream.Stream;

/** Canonical CSV dispatcher for public report surfaces. */
final class CliReportCsvRenderer {
  private CliReportCsvRenderer() {}

  static String renderAccountBalance(AccountBalanceSnapshot snapshot) {
    return CliAccountBalanceOutputRenderer.renderCsv(snapshot);
  }

  static String renderTrialBalance(TrialBalanceReport report) {
    return CliTrialBalanceCsvRenderer.render(report);
  }

  static String renderAccountLedger(AccountLedgerReport report) {
    return CliAccountLedgerCsvRenderer.render(report);
  }

  static String renderPeriodSummary(PeriodSummaryReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "recordKind",
            "subjectKind",
            "subjectCode",
            "subjectName",
            "metricName",
            "metricValue",
            "currencyCode",
            "metricUnit",
            "message"),
        CliPeriodSummaryCsvRows.rows(report));
  }

  static String renderFinancialPosition(FinancialPositionReport report) {
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateAsOf",
            "accountType",
            "lineCode",
            "lineName",
            "lineType",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        (CliStatementReportSurfacePolicy.hasComparative(report)
                ? Stream.concat(
                    CliFinancialPositionCsvRows.rows(report, "current", report.sections()),
                    CliFinancialPositionCsvRows.rows(
                        report, "comparative", report.comparativeSections()))
                : CliFinancialPositionCsvRows.rows(report, "current", report.sections()))
            .toList());
  }

  static String renderIncomeStatement(IncomeStatementReport report) {
    List<dev.erst.fingrind.core.CurrencyBalance> currentGrossProfitTotals =
        IncomeStatementGrossProfitSupport.grossProfitTotals(report);
    List<dev.erst.fingrind.core.CurrencyBalance> comparativeGrossProfitTotals =
        IncomeStatementGrossProfitSupport.comparativeGrossProfitTotals(report);
    List<IncomeStatementPresentationSupport.PresentationSection> currentSections =
        IncomeStatementPresentationSupport.currentSections(report);
    List<IncomeStatementPresentationSupport.PresentationSection> comparativeSections =
        IncomeStatementPresentationSupport.comparativeSections(report);
    return CliTextFormat.renderCsv(
        List.of(
            "exportFamily",
            "rowId",
            "parentRowId",
            "relationKind",
            "reportBasis",
            "recordKind",
            "effectiveDateFrom",
            "effectiveDateTo",
            "sectionCode",
            "lineCode",
            "lineName",
            "lineType",
            "lineClassification",
            "lineKind",
            "currencyCode",
            "debitTotal",
            "creditTotal",
            "netAmount",
            "balanceSide",
            "message"),
        (CliStatementReportSurfacePolicy.hasComparative(report)
                ? Stream.concat(
                    CliIncomeStatementCsvRows.rows(
                        report,
                        "current",
                        currentSections,
                        currentGrossProfitTotals,
                        report.netIncomeTotals()),
                    CliIncomeStatementCsvRows.rows(
                        report,
                        "comparative",
                        comparativeSections,
                        comparativeGrossProfitTotals,
                        report.comparativeNetIncomeTotals()))
                : CliIncomeStatementCsvRows.rows(
                    report,
                    "current",
                    currentSections,
                    currentGrossProfitTotals,
                    report.netIncomeTotals()))
            .toList());
  }

  static String renderCashFlowStatement(CashFlowStatementReport report) {
    return CliCashFlowCsvRenderer.renderCsv(report);
  }

  static String renderChangesInEquity(ChangesInEquityReport report) {
    return CliChangesInEquityCsvRenderer.renderCsv(report);
  }

  static String renderTaxObligation(TaxObligationReport report) {
    return CliTaxObligationCsvRenderer.render(report);
  }
}
