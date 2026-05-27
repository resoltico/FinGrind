package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerView;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryView;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceView;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read/query surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreReadView extends SqlitePostingFactStorePostingHistoryView {
  /** Returns the thread-ownership guard for this store. */
  @Override
  SqliteThreadOwner storeThreadOwner();

  /** Returns the read operations owner for this store. */
  @Override
  SqliteStoreReadOperations storeReadOperations();

  /** Returns lifecycle inspection facts for the protected book. */
  default BookLifecycleInspection inspectBook() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inspectBook();
  }

  /** Finds one registered account by account code. */
  default Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findAccount(accountCode);
  }

  /** Finds several registered accounts keyed by account code. */
  default Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findAccounts(accountCodes);
  }

  /** Returns every registered account in declaration order. */
  default List<RegisteredAccount> allAccounts() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().allAccounts();
  }

  /** Returns one page of registered accounts for the supplied query. */
  default AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().listAccounts(query);
  }

  /** Finds an existing committed posting by idempotency key. */
  default Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findExistingPosting(idempotencyKey);
  }

  /** Finds one committed posting by posting id. */
  default Optional<CommittedPosting> findPosting(PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findPosting(postingId);
  }

  /** Finds the reversal committed for the supplied prior posting when present. */
  default Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().findReversalFor(priorPostingId);
  }

  /** Returns one page of posting history for the supplied query. */
  default PostingHistoryPage listPostings(PostingHistoryQuery query) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().listPostings(query);
  }

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
