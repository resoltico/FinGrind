package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;

/** Exit-code mapping for report-producing read commands. */
final class CliReportExitCodes {
  private CliReportExitCodes() {}

  static int exitCodeFor(AccountBalanceResult result) {
    return switch (result) {
      case AccountBalanceResult.Reported _ -> 0;
      case AccountBalanceResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(TrialBalanceResult result) {
    return switch (result) {
      case TrialBalanceResult.Reported _ -> 0;
      case TrialBalanceResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(AccountLedgerResult result) {
    return switch (result) {
      case AccountLedgerResult.Reported _ -> 0;
      case AccountLedgerResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(PeriodSummaryResult result) {
    return switch (result) {
      case PeriodSummaryResult.Reported _ -> 0;
      case PeriodSummaryResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(FinancialPositionResult result) {
    return switch (result) {
      case FinancialPositionResult.Reported _ -> 0;
      case FinancialPositionResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(IncomeStatementResult result) {
    return switch (result) {
      case IncomeStatementResult.Reported _ -> 0;
      case IncomeStatementResult.Rejected _ -> 2;
    };
  }

  static int exitCodeFor(ChangesInEquityResult result) {
    return switch (result) {
      case ChangesInEquityResult.Reported _ -> 0;
      case ChangesInEquityResult.Rejected _ -> 2;
    };
  }
}
