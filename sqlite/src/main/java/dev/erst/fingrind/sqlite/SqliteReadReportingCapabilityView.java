package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookkeepingReportStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared reporting and balance defaults for SQLite read wrappers. */
interface SqliteReadReportingCapabilityView
    extends BookkeepingReportStore, SqliteLifecycleInspectionCapabilityView {
  @Override
  default Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountBalance(query);
  }

  @Override
  default List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountTotals(effectiveDateRange, postingCoverage);
  }

  @Override
  default Optional<LocalDate> latestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latestPostingEffectiveDate();
  }

  @Override
  default TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().trialBalance(query);
  }

  @Override
  default AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountLedger(query, account);
  }

  @Override
  default PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().periodSummary(query);
  }
}
