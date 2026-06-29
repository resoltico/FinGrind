package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementReport;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityReport;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionReport;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementReport;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryReport;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceReport;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import org.jspecify.annotations.Nullable;

/** Extracts reported payloads and rejections from report-family query results. */
final class CliReportResultAccess {
  private CliReportResultAccess() {}

  static @Nullable AccountBalanceSnapshot accountBalanceSnapshot(AccountBalanceResult result) {
    return result.fold(AccountBalanceResult.Reported::snapshot, rejected -> null);
  }

  static @Nullable BookQueryRejection accountBalanceRejection(AccountBalanceResult result) {
    return result.fold(reported -> null, AccountBalanceResult.Rejected::rejection);
  }

  static @Nullable TrialBalanceReport trialBalanceReport(TrialBalanceResult result) {
    return result.fold(TrialBalanceResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection trialBalanceRejection(TrialBalanceResult result) {
    return result.fold(reported -> null, TrialBalanceResult.Rejected::rejection);
  }

  static @Nullable AccountLedgerReport accountLedgerReport(AccountLedgerResult result) {
    return result.fold(AccountLedgerResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection accountLedgerRejection(AccountLedgerResult result) {
    return result.fold(reported -> null, AccountLedgerResult.Rejected::rejection);
  }

  static @Nullable PeriodSummaryReport periodSummaryReport(PeriodSummaryResult result) {
    return result.fold(PeriodSummaryResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection periodSummaryRejection(PeriodSummaryResult result) {
    return result.fold(reported -> null, PeriodSummaryResult.Rejected::rejection);
  }

  static @Nullable FinancialPositionReport financialPositionReport(FinancialPositionResult result) {
    return result.fold(FinancialPositionResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection financialPositionRejection(FinancialPositionResult result) {
    return result.fold(reported -> null, FinancialPositionResult.Rejected::rejection);
  }

  static @Nullable IncomeStatementReport incomeStatementReport(IncomeStatementResult result) {
    return result.fold(IncomeStatementResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection incomeStatementRejection(IncomeStatementResult result) {
    return result.fold(reported -> null, IncomeStatementResult.Rejected::rejection);
  }

  static @Nullable CashFlowStatementReport cashFlowStatementReport(CashFlowStatementResult result) {
    return result.fold(CashFlowStatementResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection cashFlowStatementRejection(CashFlowStatementResult result) {
    return result.fold(reported -> null, CashFlowStatementResult.Rejected::rejection);
  }

  static @Nullable ChangesInEquityReport changesInEquityReport(ChangesInEquityResult result) {
    return result.fold(ChangesInEquityResult.Reported::report, rejected -> null);
  }

  static @Nullable BookQueryRejection changesInEquityRejection(ChangesInEquityResult result) {
    return result.fold(reported -> null, ChangesInEquityResult.Rejected::rejection);
  }
}
