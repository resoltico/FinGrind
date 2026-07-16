package dev.erst.fingrind.cli;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleQuery;
import dev.erst.fingrind.contract.bookkeeping.AccrualCutoffScheduleResult;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementResult;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancingRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationResult;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.LatvianPayrollRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterQuery;
import dev.erst.fingrind.contract.bookkeeping.RealizedForeignExchangeRegisterResult;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.executor.BookReadService;
import java.util.function.BiFunction;

/** SQLite implementation of the bookkeeping report capability family. */
interface SqliteCliReportReadOperations
    extends CliBookReadWorkflow, SqliteCliReadSessionOperations {
  @Override
  default ContractDecision<AccountBalanceResult> accountBalance(
      BookAccess bookAccess, AccountBalanceQuery query) {
    return readReport(bookAccess, query, BookReadService::accountBalance);
  }

  @Override
  default ContractDecision<TrialBalanceResult> trialBalance(
      BookAccess bookAccess, TrialBalanceQuery query) {
    return readReport(bookAccess, query, BookReadService::trialBalance);
  }

  @Override
  default ContractDecision<AccountLedgerResult> accountLedger(
      BookAccess bookAccess, AccountLedgerQuery query) {
    return readReport(bookAccess, query, BookReadService::accountLedger);
  }

  @Override
  default ContractDecision<PeriodSummaryResult> periodSummary(
      BookAccess bookAccess, PeriodSummaryQuery query) {
    return readReport(bookAccess, query, BookReadService::periodSummary);
  }

  @Override
  default ContractDecision<FinancialPositionResult> financialPosition(
      BookAccess bookAccess, FinancialPositionQuery query) {
    return readReport(bookAccess, query, BookReadService::financialPosition);
  }

  @Override
  default ContractDecision<IncomeStatementResult> incomeStatement(
      BookAccess bookAccess, IncomeStatementQuery query) {
    return readReport(bookAccess, query, BookReadService::incomeStatement);
  }

  @Override
  default ContractDecision<InventoryValuationResult> inventoryValuation(
      BookAccess bookAccess, InventoryValuationQuery query) {
    return readReport(bookAccess, query, BookReadService::inventoryValuation);
  }

  @Override
  default ContractDecision<AccrualCutoffScheduleResult> accrualCutoffSchedule(
      BookAccess bookAccess, AccrualCutoffScheduleQuery query) {
    return readReport(bookAccess, query, BookReadService::accrualCutoffSchedule);
  }

  @Override
  default ContractDecision<FixedAssetRegisterResult> fixedAssetRegister(
      BookAccess bookAccess, FixedAssetRegisterQuery query) {
    return readReport(bookAccess, query, BookReadService::fixedAssetRegister);
  }

  @Override
  default ContractDecision<FinancingRegisterResult> financingRegister(
      BookAccess bookAccess, FinancingRegisterQuery query) {
    return readReport(bookAccess, query, BookReadService::financingRegister);
  }

  @Override
  default ContractDecision<RealizedForeignExchangeRegisterResult> realizedForeignExchangeRegister(
      BookAccess bookAccess, RealizedForeignExchangeRegisterQuery query) {
    return readReport(bookAccess, query, BookReadService::realizedForeignExchangeRegister);
  }

  @Override
  default ContractDecision<LatvianPayrollRegisterResult> latvianPayrollRegister(
      BookAccess bookAccess, LatvianPayrollRegisterQuery query) {
    return readReport(bookAccess, query, BookReadService::latvianPayrollRegister);
  }

  @Override
  default ContractDecision<CashFlowStatementResult> cashFlowStatement(
      BookAccess bookAccess, CashFlowStatementQuery query) {
    return readReport(bookAccess, query, BookReadService::cashFlowStatement);
  }

  @Override
  default ContractDecision<ChangesInEquityResult> changesInEquity(
      BookAccess bookAccess, ChangesInEquityQuery query) {
    return readReport(bookAccess, query, BookReadService::changesInEquity);
  }

  /** Resolves one typed report query through the protected book-read session. */
  private <QUERY, RESULT> ContractDecision<RESULT> readReport(
      BookAccess bookAccess, QUERY query, BiFunction<BookReadService, QUERY, RESULT> reportReader) {
    return withBookRead(bookAccess, service -> reportReader.apply(service, query));
  }
}
