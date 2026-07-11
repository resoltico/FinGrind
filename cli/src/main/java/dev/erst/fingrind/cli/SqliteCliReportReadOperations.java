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
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;

/** SQLite implementation of the bookkeeping report capability family. */
interface SqliteCliReportReadOperations
    extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return withBookRead(bookAccess, service -> service.accountBalance(query));
  }

  @Override
  default ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return withBookRead(bookAccess, service -> service.trialBalance(query));
  }

  @Override
  default ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return withBookRead(bookAccess, service -> service.accountLedger(query));
  }

  @Override
  default ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return withBookRead(bookAccess, service -> service.periodSummary(query));
  }

  @Override
  default ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return withBookRead(bookAccess, service -> service.financialPosition(query));
  }

  @Override
  default ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return withBookRead(bookAccess, service -> service.incomeStatement(query));
  }

  @Override
  default ContractDecision<InventoryValuationResult> inventoryValuation(
      BookAccess bookAccess, InventoryValuationQuery query) {
    return withBookRead(bookAccess, service -> service.inventoryValuation(query));
  }

  @Override
  default ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query) {
    return withBookRead(bookAccess, service -> service.cashFlowStatement(query));
  }

  @Override
  default ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return withBookRead(bookAccess, service -> service.changesInEquity(query));
  }
}
