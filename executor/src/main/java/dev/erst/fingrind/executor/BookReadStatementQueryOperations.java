package dev.erst.fingrind.executor;

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
import java.util.Objects;

/** Application ownership for financial statements and operational report queries. */
final class BookReadStatementQueryOperations {
  private final BookReportService bookReportService;

  BookReadStatementQueryOperations(BookReportService bookReportService) {
    this.bookReportService = Objects.requireNonNull(bookReportService, "bookReportService");
  }

  TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return bookReportService.trialBalance(query);
  }

  AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return bookReportService.accountLedger(query);
  }

  PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return bookReportService.periodSummary(query);
  }

  FinancialPositionResult financialPosition(FinancialPositionQuery query) {
    return bookReportService.financialPosition(query);
  }

  IncomeStatementResult incomeStatement(IncomeStatementQuery query) {
    return bookReportService.incomeStatement(query);
  }

  InventoryValuationResult inventoryValuation(InventoryValuationQuery query) {
    return bookReportService.inventoryValuation(query);
  }

  CashFlowStatementResult cashFlowStatement(CashFlowStatementQuery query) {
    return bookReportService.cashFlowStatement(query);
  }

  ChangesInEquityResult changesInEquity(ChangesInEquityQuery query) {
    return bookReportService.changesInEquity(query);
  }
}
