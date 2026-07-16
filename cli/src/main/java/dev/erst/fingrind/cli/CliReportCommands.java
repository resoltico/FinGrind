package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().accountBalance());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().trialBalance());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().accountLedger());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().periodSummary());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().financialPosition());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().incomeStatement());
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

/** Report command that projects durable accrual cut-off lifecycle balances. */
final class AccrualCutoffSchedule extends CliBookQueryReportCommand<AccrualCutoffScheduleQuery> {
  AccrualCutoffSchedule(
      BookAccess bookAccess, AccrualCutoffScheduleQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      AccrualCutoffScheduleQuery query,
      CliReportOutput output) {
    return executionContext.report().runAccrualCutoffScheduleCommand(bookAccess, query, output);
  }
}

/** Report command that projects the durable fixed-asset lifecycle register. */
final class FixedAssetRegister extends CliBookQueryReportCommand<FixedAssetRegisterQuery> {
  FixedAssetRegister(BookAccess bookAccess, FixedAssetRegisterQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext context,
      BookAccess bookAccess,
      FixedAssetRegisterQuery query,
      CliReportOutput output) {
    return context.report().runFixedAssetRegisterCommand(bookAccess, query, output);
  }
}

/** Report command that projects durable financing principal and interest state. */
final class FinancingRegister extends CliBookQueryReportCommand<FinancingRegisterQuery> {
  FinancingRegister(BookAccess bookAccess, FinancingRegisterQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      FinancingRegisterQuery query,
      CliReportOutput output) {
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess,
            query,
            output,
            CliOperationalReportCommandHandlers.financingRegister(
                executionContext.report().handlers().readWorkflow(),
                executionContext.report().handlers().responseWriter()));
  }
}

/** Report command that projects durable foreign-currency obligation and settlement state. */
final class RealizedForeignExchangeRegister
    extends CliBookQueryReportCommand<RealizedForeignExchangeRegisterQuery> {
  RealizedForeignExchangeRegister(
      BookAccess bookAccess, RealizedForeignExchangeRegisterQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      RealizedForeignExchangeRegisterQuery query,
      CliReportOutput output) {
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess,
            query,
            output,
            CliOperationalReportCommandHandlers.realizedForeignExchangeRegister(
                executionContext.report().handlers().readWorkflow(),
                executionContext.report().handlers().responseWriter()));
  }
}

/**
 * Report CLI command that projects immutable Latvian payroll calculations and settlement lineage.
 */
final class LatvianPayrollRegister extends CliBookQueryReportCommand<LatvianPayrollRegisterQuery> {
  LatvianPayrollRegister(
      BookAccess bookAccess, LatvianPayrollRegisterQuery query, CliReportOutput output) {
    super(bookAccess, query, output);
  }

  @Override
  protected int executeCommand(
      CliExecutionContext executionContext,
      BookAccess bookAccess,
      LatvianPayrollRegisterQuery query,
      CliReportOutput output) {
    return executionContext.report().runLatvianPayrollRegisterCommand(bookAccess, query, output);
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().cashFlowStatement());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().changesInEquity());
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
    return executionContext
        .report()
        .runConfiguredReportCommand(
            bookAccess, query, output, executionContext.report().handlers().taxObligation());
  }
}
