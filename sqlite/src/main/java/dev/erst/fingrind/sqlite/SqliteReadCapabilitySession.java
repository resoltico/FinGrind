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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Read-only wrapper over the shared SQLite store core. */
class SqliteReadCapabilitySession extends SqliteDelegatingSession implements SqliteReadSession {
  SqliteReadCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public BookLifecycleInspection inspectBook() {
    return store.inspectBook();
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return store.findAccount(accountCode);
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    return store.findAccounts(accountCodes);
  }

  @Override
  public List<RegisteredAccount> allAccounts() {
    return store.allAccounts();
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return store.listAccounts(query);
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return store.findExistingPosting(idempotencyKey);
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return store.findPosting(postingId);
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return store.findReversalFor(priorPostingId);
  }

  @Override
  public PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return store.listPostings(query);
  }

  @Override
  public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    return store.accountBalance(query);
  }

  @Override
  public List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    return store.accountTotals(effectiveDateRange, postingCoverage);
  }

  @Override
  public Optional<LocalDate> latestPostingEffectiveDate() {
    return store.latestPostingEffectiveDate();
  }

  @Override
  public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    return store.trialBalance(query);
  }

  @Override
  public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    return store.accountLedger(query, account);
  }

  @Override
  public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    return store.periodSummary(query);
  }

  @Override
  public void close() {
    closeStore();
  }
}
