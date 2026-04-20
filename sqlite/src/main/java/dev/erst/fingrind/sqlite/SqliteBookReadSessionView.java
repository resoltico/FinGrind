package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.BookReadSession;
import java.util.Objects;
import java.util.Optional;

/** Narrow unified read-session view over one SQLite-backed store. */
final class SqliteBookReadSessionView implements BookReadSession {
  private final SqlitePostingFactStore store;

  SqliteBookReadSessionView(SqlitePostingFactStore store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  @Override
  public BookInspection inspectBook() {
    return store.inspectBook();
  }

  @Override
  public boolean isInitialized() {
    return store.isInitialized();
  }

  @Override
  public AccountPage listAccounts(ListAccountsQuery query) {
    store.ensureOpenSession();
    try {
      return SqliteStatementQuerySupport.loadAccountPage(store.initializedQueryDatabase(), query);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    store.ensureOpenSession();
    try {
      return SqliteStatementQuerySupport.findOneAccount(
          store.initializedQueryDatabase(), accountCode);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    store.ensureOpenSession();
    try {
      return store
          .postingReadSupport()
          .findOnePosting(
              store.initializedQueryDatabase(),
              SqlitePostingSql.FIND_POSTING_BY_ID,
              statement -> statement.bindText(1, postingId.value()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public PostingPage listPostings(ListPostingsQuery query) {
    store.ensureOpenSession();
    try {
      return store.postingReadSupport().loadPostingPage(store.initializedQueryDatabase(), query);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<AccountBalanceSnapshot> accountBalance(AccountBalanceQuery query) {
    store.ensureOpenSession();
    try {
      return store.postingReadSupport().accountBalance(store.initializedQueryDatabase(), query);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public TrialBalanceReport trialBalance(TrialBalanceQuery query) {
    store.ensureOpenSession();
    try {
      return store.reportReadSupport().trialBalance(store.initializedQueryDatabase(), query);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public AccountLedgerReport accountLedger(AccountLedgerQuery query, DeclaredAccount account) {
    store.ensureOpenSession();
    try {
      return store
          .reportReadSupport()
          .accountLedger(store.initializedQueryDatabase(), query, account);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public PeriodSummaryReport periodSummary(PeriodSummaryQuery query) {
    store.ensureOpenSession();
    try {
      return store.reportReadSupport().periodSummary(store.initializedQueryDatabase(), query);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }
}
