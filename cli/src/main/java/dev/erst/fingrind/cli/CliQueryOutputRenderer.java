package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceReport;
import java.nio.file.Path;

/** Facade that routes query and reporting payloads to narrower human/CSV renderers. */
final class CliQueryOutputRenderer {
  private CliQueryOutputRenderer() {}

  static String renderBookInspectionHuman(Path bookFilePath, BookInspection inspection) {
    return CliBookInspectionOutputRenderer.renderHuman(bookFilePath, inspection);
  }

  static String renderAccountsHuman(AccountPage page) {
    return CliAccountPageOutputRenderer.renderHuman(page);
  }

  static String renderAccountsCsv(AccountPage page) {
    return CliAccountPageOutputRenderer.renderCsv(page);
  }

  static String renderPostingHuman(PostingFact postingFact) {
    return CliPostingOutputRenderer.renderPostingHuman(postingFact);
  }

  static String renderPostingRegisterHuman(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterHuman(page);
  }

  static String renderPostingRegisterCsv(PostingPage page) {
    return CliPostingOutputRenderer.renderPostingRegisterCsv(page);
  }

  static String renderAccountBalanceHuman(AccountBalanceSnapshot snapshot) {
    return CliAccountBalanceOutputRenderer.renderHuman(snapshot);
  }

  static String renderAccountBalanceCsv(AccountBalanceSnapshot snapshot) {
    return CliAccountBalanceOutputRenderer.renderCsv(snapshot);
  }

  static String renderTrialBalanceHuman(TrialBalanceReport report) {
    return CliReportOutputRenderer.renderTrialBalanceHuman(report);
  }

  static String renderTrialBalanceCsv(TrialBalanceReport report) {
    return CliReportOutputRenderer.renderTrialBalanceCsv(report);
  }

  static String renderAccountLedgerHuman(AccountLedgerReport report) {
    return CliReportOutputRenderer.renderAccountLedgerHuman(report);
  }

  static String renderAccountLedgerCsv(AccountLedgerReport report) {
    return CliReportOutputRenderer.renderAccountLedgerCsv(report);
  }

  static String renderPeriodSummaryHuman(PeriodSummaryReport report) {
    return CliReportOutputRenderer.renderPeriodSummaryHuman(report);
  }

  static String renderPeriodSummaryCsv(PeriodSummaryReport report) {
    return CliReportOutputRenderer.renderPeriodSummaryCsv(report);
  }
}
