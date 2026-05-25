package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.runtime.BookInspection;
import java.nio.file.Path;

/** Facade that routes query and reporting payloads to narrower text/CSV renderers. */
final class CliQueryOutputRenderer {
  private CliQueryOutputRenderer() {}

  static String renderBookInspectionText(Path bookFilePath, BookInspection inspection) {
    return CliBookInspectionOutputRenderer.renderText(bookFilePath, inspection);
  }

  static String renderAccountsText(AccountPage page) {
    return CliAccountPageOutputRenderer.renderText(page);
  }

  static String renderAccountsCsv(AccountPage page) {
    return CliAccountPageOutputRenderer.renderCsv(page);
  }

  static String renderPostingText(
      dev.erst.fingrind.core.BookIdentity bookIdentity,
      dev.erst.fingrind.contract.bookkeeping.PostingFact postingFact) {
    return CliPostingOutputRenderer.renderPostingText(bookIdentity, postingFact);
  }

  static String renderPostingRegisterText(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterText(page);
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterCsv(page);
  }

  static String renderAccountBalanceText(AccountBalanceSnapshot snapshot) {
    return CliAccountBalanceOutputRenderer.renderText(snapshot);
  }

  static String renderAccountBalanceCsv(AccountBalanceSnapshot snapshot) {
    return CliAccountBalanceOutputRenderer.renderCsv(snapshot);
  }

  static String renderTrialBalanceText(TrialBalanceReport report) {
    return CliReportOutputRenderer.renderTrialBalanceText(report);
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliReportOutputRenderer.renderTrialBalanceCsv(report);
  }

  static String renderAccountLedgerText(AccountLedgerReport report) {
    return CliReportOutputRenderer.renderAccountLedgerText(report);
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliReportOutputRenderer.renderAccountLedgerCsv(report);
  }

  static String renderPeriodSummaryText(PeriodSummaryReport report) {
    return CliReportOutputRenderer.renderPeriodSummaryText(report);
  }

  static String renderPeriodSummaryCsv(PeriodSummaryReport report) {
    return CliReportOutputRenderer.renderPeriodSummaryCsv(report);
  }

  static String renderFinancialPositionText(FinancialPositionReport report) {
    return CliReportOutputRenderer.renderFinancialPositionText(report);
  }

  static String renderFinancialPositionCsv(FinancialPositionReport report) {
    return CliReportOutputRenderer.renderFinancialPositionCsv(report);
  }

  static String renderIncomeStatementText(IncomeStatementReport report) {
    return CliReportOutputRenderer.renderIncomeStatementText(report);
  }

  static String renderIncomeStatementCsv(IncomeStatementReport report) {
    return CliReportOutputRenderer.renderIncomeStatementCsv(report);
  }

  static String renderChangesInEquityText(ChangesInEquityReport report) {
    return CliReportOutputRenderer.renderChangesInEquityText(report);
  }

  static String renderChangesInEquityCsv(ChangesInEquityReport report) {
    return CliReportOutputRenderer.renderChangesInEquityCsv(report);
  }
}
