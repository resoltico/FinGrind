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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Reporting read surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreReportingView extends SqlitePostingFactStoreReadOperationsView {
  /** Returns the balance view for one account when the account exists. */
  default Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountBalance(query);
  }

  /** Returns account totals across the supplied date range and posting coverage. */
  default List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountTotals(effectiveDateRange, postingCoverage);
  }

  /** Returns the latest committed posting effective date when the initialized book has postings. */
  default Optional<LocalDate> latestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().latestPostingEffectiveDate();
  }

  /** Returns the trial balance view for the supplied criteria. */
  default TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().trialBalance(query);
  }

  /** Returns the ledger view for one account under the supplied criteria. */
  default AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().accountLedger(query, account);
  }

  /** Returns the period summary view for the supplied criteria. */
  default PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().periodSummary(query);
  }
}
