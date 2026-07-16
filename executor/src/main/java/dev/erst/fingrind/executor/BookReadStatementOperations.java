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

/** Statement and operational-report capability exposed by {@link BookReadService}. */
public sealed interface BookReadStatementOperations permits BookReadService {
  /** Computes one book-wide trial balance. */
  default TrialBalanceResult trialBalance(TrialBalanceQuery query) {
    return ((BookReadService) this).statementQueries().trialBalance(query);
  }

  /** Computes one running ledger for the selected declared account. */
  default AccountLedgerResult accountLedger(AccountLedgerQuery query) {
    return ((BookReadService) this).statementQueries().accountLedger(query);
  }

  /** Computes one bounded period summary for the selected book. */
  default PeriodSummaryResult periodSummary(PeriodSummaryQuery query) {
    return ((BookReadService) this).statementQueries().periodSummary(query);
  }

  /** Computes one statement of financial position for the selected book. */
  default FinancialPositionResult financialPosition(FinancialPositionQuery query) {
    return ((BookReadService) this).statementQueries().financialPosition(query);
  }

  /** Computes one income statement for the selected book and reporting period. */
  default IncomeStatementResult incomeStatement(IncomeStatementQuery query) {
    return ((BookReadService) this).statementQueries().incomeStatement(query);
  }

  /** Computes exact inventory carrying values from canonical durable movement replay. */
  default InventoryValuationResult inventoryValuation(InventoryValuationQuery query) {
    return ((BookReadService) this).statementQueries().inventoryValuation(query);
  }

  /** Computes one statement of cash receipts and payments for the selected book and period. */
  default CashFlowStatementResult cashFlowStatement(CashFlowStatementQuery query) {
    return ((BookReadService) this).statementQueries().cashFlowStatement(query);
  }

  /** Computes one statement of changes in equity for the selected book and reporting period. */
  default ChangesInEquityResult changesInEquity(ChangesInEquityQuery query) {
    return ((BookReadService) this).statementQueries().changesInEquity(query);
  }
}
