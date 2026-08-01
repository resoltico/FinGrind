package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.reportmodel.AccountBalanceReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.AccountLedgerReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.CashFlowStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.ChangesInEquityReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.FinancialPositionReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.IncomeStatementReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.PeriodSummaryReportModelBuilder;
import dev.erst.fingrind.contract.reportmodel.TrialBalanceReportModelBuilder;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.BookIdentity;
import java.nio.file.Path;
import java.time.Instant;

/** Test-only renderer for query-output assertions. */
final class CliQueryOutputRenderer {
  private static final Instant GENERATED_AT = Instant.EPOCH;

  private CliQueryOutputRenderer() {}

  static String renderBookInspectionText(Path bookFilePath, BookInspection inspection) {
    return CliBookInspectionOutputRenderer.renderText(bookFilePath, inspection);
  }

  static String renderAccountsText(AccountPage page) {
    return CliAccountPageOutputRenderer.renderText(page, false);
  }

  static String renderAccountsCsv(AccountPage page) {
    return CliAccountPageOutputRenderer.renderCsv(page);
  }

  static String renderPostingText(BookIdentity bookIdentity, PostingFact postingFact) {
    return CliPostingOutputRenderer.renderPostingText(bookIdentity, postingFact, null, false);
  }

  static String renderPostingRegisterText(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterText(page, false);
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterCsv(page);
  }

  static String renderAccountBalanceText(AccountBalanceSnapshot snapshot) {
    return TextReportProjector.render(AccountBalanceReportModelBuilder.buildModel(snapshot));
  }

  static String renderAccountBalanceCsv(AccountBalanceSnapshot snapshot) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.accountBalance(snapshot, GENERATED_AT));
  }

  static String renderTrialBalanceText(TrialBalanceReport report) {
    return TextReportProjector.render(TrialBalanceReportModelBuilder.buildModel(report));
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.trialBalance(report, GENERATED_AT));
  }

  static String renderAccountLedgerText(AccountLedgerReport report) {
    return TextReportProjector.render(AccountLedgerReportModelBuilder.buildModel(report));
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.accountLedger(report, GENERATED_AT));
  }

  static String renderPeriodSummaryText(PeriodSummaryReport report) {
    return TextReportProjector.render(PeriodSummaryReportModelBuilder.buildModel(report));
  }

  static String renderPeriodSummaryCsv(PeriodSummaryReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.periodSummary(report, GENERATED_AT));
  }

  static String renderFinancialPositionText(FinancialPositionReport report) {
    return TextReportProjector.render(FinancialPositionReportModelBuilder.buildModel(report));
  }

  static String renderFinancialPositionCsv(FinancialPositionReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.financialPosition(report, GENERATED_AT));
  }

  static String renderIncomeStatementText(IncomeStatementReport report) {
    return TextReportProjector.render(IncomeStatementReportModelBuilder.buildModel(report));
  }

  static String renderIncomeStatementCsv(IncomeStatementReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.incomeStatement(report, GENERATED_AT));
  }

  static String renderCashFlowStatementText(CashFlowStatementReport report) {
    return TextReportProjector.render(CashFlowStatementReportModelBuilder.buildModel(report));
  }

  static String renderCashFlowStatementCsv(CashFlowStatementReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.cashFlowStatement(report, GENERATED_AT));
  }

  static String renderChangesInEquityText(ChangesInEquityReport report) {
    return TextReportProjector.render(ChangesInEquityReportModelBuilder.buildModel(report));
  }

  static String renderChangesInEquityCsv(ChangesInEquityReport report) {
    return CliSemanticReportCsvRenderer.render(
        CliReportPayloadMapper.changesInEquity(report, GENERATED_AT));
  }
}
