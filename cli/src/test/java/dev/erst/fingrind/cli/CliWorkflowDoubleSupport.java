package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.OpenBookResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.PublicPathHint;
import dev.erst.fingrind.contract.tax.TaxObligationResult;
import java.nio.file.Path;

/** Thin CLI workflow fixture layer that exposes focused workflow doubles to test suites. */
class CliWorkflowDoubleSupport extends CliFixtureSupport {
  protected static BookAccess bookAccess(Path bookFilePath, Path bookKeyFilePath) {
    return new BookAccess(bookFilePath, new BookAccess.PassphraseSource.KeyFile(bookKeyFilePath));
  }

  /** Backward-compatible test alias that preserves concise fixture call sites. */
  static final class RecordingWorkflow extends CliRecordingWorkflow {
    RecordingWorkflow(
        OpenBookResult openBookResult,
        RekeyBookResult rekeyBookResult,
        DeclareAccountResult declareAccountResult,
        ListAccountsResult listAccountsResult,
        PreflightEntryResult preflightResult,
        CommitEntryResult commitResult) {
      super(
          openBookResult,
          rekeyBookResult,
          declareAccountResult,
          listAccountsResult,
          preflightResult,
          commitResult);
    }
  }

  /** Backward-compatible test alias for the focused exploding workflow double. */
  static final class ExplodingWorkflow extends CliExplodingWorkflow {
    ExplodingWorkflow(RuntimeException failure) {
      super(failure);
    }
  }

  /** Backward-compatible test alias for invalid-request workflow failures. */
  protected static final class IllegalArgumentWorkflow extends CliIllegalArgumentWorkflow {}

  protected static CliBookWorkflow reportingWorkflow(TrialBalanceResult trialBalanceResult) {
    return reportingWorkflow(
        new AccountBalanceResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        rejectedTaxObligationResult(),
        trialBalanceResult,
        new AccountLedgerResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new PeriodSummaryResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new FinancialPositionResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new CashFlowStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new ChangesInEquityResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()));
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TaxObligationResult taxObligationResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult) {
    return reportingWorkflow(
        accountBalanceResult,
        taxObligationResult,
        trialBalanceResult,
        accountLedgerResult,
        periodSummaryResult,
        new FinancialPositionResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new CashFlowStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        new ChangesInEquityResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()));
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TaxObligationResult taxObligationResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult,
      FinancialPositionResult financialPositionResult,
      dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult incomeStatementResult,
      ChangesInEquityResult changesInEquityResult) {
    return reportingWorkflow(
        accountBalanceResult,
        taxObligationResult,
        trialBalanceResult,
        accountLedgerResult,
        periodSummaryResult,
        financialPositionResult,
        incomeStatementResult,
        new CashFlowStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized()),
        changesInEquityResult);
  }

  protected static CliBookWorkflow reportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TaxObligationResult taxObligationResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult,
      FinancialPositionResult financialPositionResult,
      dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult incomeStatementResult,
      CashFlowStatementResult cashFlowStatementResult,
      ChangesInEquityResult changesInEquityResult) {
    return new CliReportingWorkflow(
        accountBalanceResult,
        taxObligationResult,
        trialBalanceResult,
        accountLedgerResult,
        periodSummaryResult,
        financialPositionResult,
        incomeStatementResult,
        cashFlowStatementResult,
        changesInEquityResult);
  }

  protected static TaxObligationResult rejectedTaxObligationResult() {
    return new TaxObligationResult.Rejected(
        new dev.erst.fingrind.contract.tax.TaxQueryRejection.BookNotInitialized());
  }

  protected static <T> ContractDecision<T> accepted(T value) {
    return ContractDecision.accepted(value);
  }

  protected static PublicPathHint hint(Path path) {
    return PublicPathHint.fromPath(path);
  }
}
