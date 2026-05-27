package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;

/** Facade that routes report rendering to focused per-report-family renderers. */
final class CliReportOutputRenderer {
  private CliReportOutputRenderer() {}

  static String renderTrialBalanceText(TrialBalanceReport report) {
    return CliTrialBalanceReportRenderer.renderText(report);
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliTrialBalanceReportRenderer.renderCsv(report);
  }

  static String renderAccountLedgerText(AccountLedgerReport report) {
    return CliAccountLedgerReportRenderer.renderText(report);
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliAccountLedgerReportRenderer.renderCsv(report);
  }

  static String renderPeriodSummaryText(PeriodSummaryReport report) {
    return CliPeriodSummaryReportRenderer.renderText(report);
  }

  static String renderPeriodSummaryCsv(PeriodSummaryReport report) {
    return CliPeriodSummaryReportRenderer.renderCsv(report);
  }

  static String renderFinancialPositionText(FinancialPositionReport report) {
    return CliFinancialPositionReportRenderer.renderText(report);
  }

  static String renderFinancialPositionCsv(FinancialPositionReport report) {
    return CliFinancialPositionReportRenderer.renderCsv(report);
  }

  static String renderIncomeStatementText(IncomeStatementReport report) {
    return CliIncomeStatementReportRenderer.renderText(report);
  }

  static String renderIncomeStatementCsv(IncomeStatementReport report) {
    return CliIncomeStatementReportRenderer.renderCsv(report);
  }

  static String renderChangesInEquityText(ChangesInEquityReport report) {
    return CliChangesInEquityReportRenderer.renderText(report);
  }

  static String renderChangesInEquityCsv(ChangesInEquityReport report) {
    return CliChangesInEquityReportRenderer.renderCsv(report);
  }
}
