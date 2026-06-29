package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** Report-focused workflow double that only serves query/report projections. */
class CliReportingWorkflow extends CliBookWorkflowAdapter {
  private final AccountBalanceResult accountBalanceResult;
  private final TrialBalanceResult trialBalanceResult;
  private final AccountLedgerResult accountLedgerResult;
  private final PeriodSummaryResult periodSummaryResult;
  private final FinancialPositionResult financialPositionResult;
  private final IncomeStatementResult incomeStatementResult;
  private final CashFlowStatementResult cashFlowStatementResult;
  private final ChangesInEquityResult changesInEquityResult;

  CliReportingWorkflow(
      AccountBalanceResult accountBalanceResult,
      TrialBalanceResult trialBalanceResult,
      AccountLedgerResult accountLedgerResult,
      PeriodSummaryResult periodSummaryResult,
      FinancialPositionResult financialPositionResult,
      IncomeStatementResult incomeStatementResult,
      CashFlowStatementResult cashFlowStatementResult,
      ChangesInEquityResult changesInEquityResult) {
    this.accountBalanceResult = accountBalanceResult;
    this.trialBalanceResult = trialBalanceResult;
    this.accountLedgerResult = accountLedgerResult;
    this.periodSummaryResult = periodSummaryResult;
    this.financialPositionResult = financialPositionResult;
    this.incomeStatementResult = incomeStatementResult;
    this.cashFlowStatementResult = cashFlowStatementResult;
    this.changesInEquityResult = changesInEquityResult;
  }

  @Override
  public ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return CliWorkflowDoubleSupport.accepted(accountBalanceResult);
  }

  @Override
  public ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return CliWorkflowDoubleSupport.accepted(trialBalanceResult);
  }

  @Override
  public ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return CliWorkflowDoubleSupport.accepted(accountLedgerResult);
  }

  @Override
  public ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return CliWorkflowDoubleSupport.accepted(periodSummaryResult);
  }

  @Override
  public ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return CliWorkflowDoubleSupport.accepted(financialPositionResult);
  }

  @Override
  public ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return CliWorkflowDoubleSupport.accepted(incomeStatementResult);
  }

  @Override
  public ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query) {
    return CliWorkflowDoubleSupport.accepted(cashFlowStatementResult);
  }

  @Override
  public ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return CliWorkflowDoubleSupport.accepted(changesInEquityResult);
  }
}
