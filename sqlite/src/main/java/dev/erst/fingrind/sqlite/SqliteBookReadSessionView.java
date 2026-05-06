package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadSession;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceView;
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
import java.util.Objects;
import java.util.Optional;

/** Narrow unified read-session view over one SQLite-backed store. */
final class SqliteBookReadSessionView implements BookReadSession {
  private final SqliteStoreReadOperations readOperations;

  SqliteBookReadSessionView(SqliteStoreReadOperations readOperations) {
    this.readOperations = Objects.requireNonNull(readOperations, "readOperations");
  }

  @Override
  public BookInspection inspectBook() {
    return readOperations.inspectBook();
  }

  @Override
  public boolean isInitialized() {
    return readOperations.isInitialized();
  }

  @Override
  public AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    return readOperations.listAccounts(query);
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return readOperations.findAccount(accountCode);
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return readOperations.findPosting(postingId);
  }

  @Override
  public PostingHistoryPage listPostings(PostingHistoryQuery query) {
    return readOperations.listPostings(query);
  }

  @Override
  public Optional<AccountBalanceView> accountBalance(AccountBalanceCriteria query) {
    return readOperations.accountBalance(query);
  }

  @Override
  public TrialBalanceView trialBalance(TrialBalanceCriteria query) {
    return readOperations.trialBalance(query);
  }

  @Override
  public AccountLedgerView accountLedger(AccountLedgerCriteria query, RegisteredAccount account) {
    return readOperations.accountLedger(query, account);
  }

  @Override
  public PeriodSummaryView periodSummary(PeriodSummaryCriteria query) {
    return readOperations.periodSummary(query);
  }
}
