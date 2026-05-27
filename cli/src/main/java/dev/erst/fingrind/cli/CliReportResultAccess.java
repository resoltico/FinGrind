package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
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
    return switch (result) {
      case AccountBalanceResult.Reported reported -> reported.snapshot();
      case AccountBalanceResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection accountBalanceRejection(AccountBalanceResult result) {
    return switch (result) {
      case AccountBalanceResult.Reported _ -> null;
      case AccountBalanceResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable TrialBalanceReport trialBalanceReport(TrialBalanceResult result) {
    return switch (result) {
      case TrialBalanceResult.Reported reported -> reported.report();
      case TrialBalanceResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection trialBalanceRejection(TrialBalanceResult result) {
    return switch (result) {
      case TrialBalanceResult.Reported _ -> null;
      case TrialBalanceResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable AccountLedgerReport accountLedgerReport(AccountLedgerResult result) {
    return switch (result) {
      case AccountLedgerResult.Reported reported -> reported.report();
      case AccountLedgerResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection accountLedgerRejection(AccountLedgerResult result) {
    return switch (result) {
      case AccountLedgerResult.Reported _ -> null;
      case AccountLedgerResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable PeriodSummaryReport periodSummaryReport(PeriodSummaryResult result) {
    return switch (result) {
      case PeriodSummaryResult.Reported reported -> reported.report();
      case PeriodSummaryResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection periodSummaryRejection(PeriodSummaryResult result) {
    return switch (result) {
      case PeriodSummaryResult.Reported _ -> null;
      case PeriodSummaryResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable FinancialPositionReport financialPositionReport(FinancialPositionResult result) {
    return switch (result) {
      case FinancialPositionResult.Reported reported -> reported.report();
      case FinancialPositionResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection financialPositionRejection(FinancialPositionResult result) {
    return switch (result) {
      case FinancialPositionResult.Reported _ -> null;
      case FinancialPositionResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable IncomeStatementReport incomeStatementReport(IncomeStatementResult result) {
    return switch (result) {
      case IncomeStatementResult.Reported reported -> reported.report();
      case IncomeStatementResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection incomeStatementRejection(IncomeStatementResult result) {
    return switch (result) {
      case IncomeStatementResult.Reported _ -> null;
      case IncomeStatementResult.Rejected rejected -> rejected.rejection();
    };
  }

  static @Nullable ChangesInEquityReport changesInEquityReport(ChangesInEquityResult result) {
    return switch (result) {
      case ChangesInEquityResult.Reported reported -> reported.report();
      case ChangesInEquityResult.Rejected _ -> null;
    };
  }

  static @Nullable BookQueryRejection changesInEquityRejection(ChangesInEquityResult result) {
    return switch (result) {
      case ChangesInEquityResult.Reported _ -> null;
      case ChangesInEquityResult.Rejected rejected -> rejected.rejection();
    };
  }
}
