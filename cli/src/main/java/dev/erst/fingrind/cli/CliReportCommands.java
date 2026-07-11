package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.tax.TaxObligationQuery;

/** Report CLI commands that can render to terminal output or a PDF file. */
final class AccountBalance extends CliBookQueryReportCommand<AccountBalanceQuery> {
  AccountBalance(BookAccess bookAccess, AccountBalanceQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      AccountBalanceQuery query,
      CliReportOutput output) {
    return executionContext.report().runAccountBalanceCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class TrialBalance extends CliBookQueryReportCommand<TrialBalanceQuery> {
  TrialBalance(BookAccess bookAccess, TrialBalanceQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      TrialBalanceQuery query,
      CliReportOutput output) {
    return executionContext.report().runTrialBalanceCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class AccountLedger extends CliBookQueryReportCommand<AccountLedgerQuery> {
  AccountLedger(BookAccess bookAccess, AccountLedgerQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      AccountLedgerQuery query,
      CliReportOutput output) {
    return executionContext.report().runAccountLedgerCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class PeriodSummary extends CliBookQueryReportCommand<PeriodSummaryQuery> {
  PeriodSummary(BookAccess bookAccess, PeriodSummaryQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      PeriodSummaryQuery query,
      CliReportOutput output) {
    return executionContext.report().runPeriodSummaryCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class FinancialPosition extends CliBookQueryReportCommand<FinancialPositionQuery> {
  FinancialPosition(BookAccess bookAccess, FinancialPositionQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      FinancialPositionQuery query,
      CliReportOutput output) {
    return executionContext.report().runFinancialPositionCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class IncomeStatement extends CliBookQueryReportCommand<IncomeStatementQuery> {
  IncomeStatement(BookAccess bookAccess, IncomeStatementQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      IncomeStatementQuery query,
      CliReportOutput output) {
    return executionContext.report().runIncomeStatementCommand(bookAccess, query, output);
  }
}

/** Report command that projects exact inventory carrying values and optional movement detail. */
final class InventoryValuation extends CliBookQueryReportCommand<InventoryValuationQuery> {
  InventoryValuation(BookAccess bookAccess, InventoryValuationQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      InventoryValuationQuery query,
      CliReportOutput output) {
    return executionContext.report().runInventoryValuationCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class CashFlowStatement extends CliBookQueryReportCommand<CashFlowStatementQuery> {
  CashFlowStatement(BookAccess bookAccess, CashFlowStatementQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      CashFlowStatementQuery query,
      CliReportOutput output) {
    return executionContext.report().runCashFlowStatementCommand(bookAccess, query, output);
  }
}

/** Report CLI commands that can render to terminal output or a PDF file. */
final class ChangesInEquity extends CliBookQueryReportCommand<ChangesInEquityQuery> {
  ChangesInEquity(BookAccess bookAccess, ChangesInEquityQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      ChangesInEquityQuery query,
      CliReportOutput output) {
    return executionContext.report().runChangesInEquityCommand(bookAccess, query, output);
  }
}

/** Report CLI command that computes one bounded tax-obligation report. */
final class TaxObligation extends CliBookQueryReportCommand<TaxObligationQuery> {
  TaxObligation(BookAccess bookAccess, TaxObligationQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      TaxObligationQuery query,
      CliReportOutput output) {
    return executionContext.report().runTaxObligationCommand(bookAccess, query, output);
  }
}
