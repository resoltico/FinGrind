package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerQuery;
import dev.erst.fingrind.contract.bookkeeping.CashFlowStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.ChangesInEquityQuery;
import dev.erst.fingrind.contract.bookkeeping.FinancialPositionQuery;
import dev.erst.fingrind.contract.bookkeeping.IncomeStatementQuery;
import dev.erst.fingrind.contract.bookkeeping.InventoryValuationQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.PeriodSummaryQuery;
import dev.erst.fingrind.contract.bookkeeping.TrialBalanceQuery;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryCursor;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CashFlowStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.ChangesInEquityCriteria;
import dev.erst.fingrind.executor.bookkeeping.FinancialPositionCriteria;
import dev.erst.fingrind.executor.bookkeeping.IncomeStatementCriteria;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryCursor;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import java.util.Objects;

/** Internal query translator for the public read-side application service. */
final class BookReadQueryTranslator {
  private BookReadQueryTranslator() {}

  static AccountRegistryQuery fromPublished(ListAccountsQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountRegistryQuery(
        query.limit(),
        query.cursor().map(cursor -> new AccountRegistryCursor(cursor.accountCode())));
  }

  static PostingHistoryQuery fromPublished(ListPostingsQuery query) {
    Objects.requireNonNull(query, "query");
    return new PostingHistoryQuery(
        query.accountCode(),
        query.effectiveDateRange(),
        query.limit(),
        query
            .cursor()
            .map(
                cursor ->
                    new PostingHistoryCursor(
                        cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId())));
  }

  static AccountBalanceCriteria fromPublished(AccountBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountBalanceCriteria(
        query.accountCode(), query.effectiveDateRange(), query.postingCoverage());
  }

  static TrialBalanceCriteria fromPublished(TrialBalanceQuery query) {
    Objects.requireNonNull(query, "query");
    return new TrialBalanceCriteria(
        query.effectiveDateAsOf(), query.postingCoverage(), query.comparativeSelection());
  }

  static AccountLedgerCriteria fromPublished(AccountLedgerQuery query) {
    Objects.requireNonNull(query, "query");
    return new AccountLedgerCriteria(
        query.accountCode(),
        query.effectiveDateRange(),
        query.postingCoverage(),
        query.limit(),
        query
            .cursor()
            .map(
                cursor ->
                    new AccountLedgerCursor(
                        cursor.effectiveDate(), cursor.recordedAt(), cursor.postingId())));
  }

  static PeriodSummaryCriteria fromPublished(PeriodSummaryQuery query) {
    Objects.requireNonNull(query, "query");
    return new PeriodSummaryCriteria(
        query.effectiveDateFrom(), query.effectiveDateTo(), query.postingCoverage());
  }

  static FinancialPositionCriteria fromPublished(FinancialPositionQuery query) {
    Objects.requireNonNull(query, "query");
    return new FinancialPositionCriteria(query.effectiveDateAsOf(), query.comparativeSelection());
  }

  static IncomeStatementCriteria fromPublished(IncomeStatementQuery query) {
    Objects.requireNonNull(query, "query");
    return new IncomeStatementCriteria(
        query.effectiveDateFrom(), query.effectiveDateTo(), query.comparativeSelection());
  }

  static InventoryValuationCriteria fromPublished(InventoryValuationQuery query) {
    Objects.requireNonNull(query, "query");
    return new InventoryValuationCriteria(query.effectiveDateAsOf(), query.includeMovements());
  }

  static CashFlowStatementCriteria fromPublished(CashFlowStatementQuery query) {
    Objects.requireNonNull(query, "query");
    return new CashFlowStatementCriteria(
        query.effectiveDateFrom(), query.effectiveDateTo(), query.comparativeSelection());
  }

  static ChangesInEquityCriteria fromPublished(ChangesInEquityQuery query) {
    Objects.requireNonNull(query, "query");
    return new ChangesInEquityCriteria(
        query.effectiveDateFrom(), query.effectiveDateTo(), query.comparativeSelection());
  }
}
