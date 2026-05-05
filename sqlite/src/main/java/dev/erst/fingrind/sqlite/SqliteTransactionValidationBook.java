package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingValidationBook;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transaction-scoped validation view that rechecks posting invariants inside SQLite writes. */
final class SqliteTransactionValidationBook implements PostingValidationBook {
  private final SqliteNativeDatabase activeDatabase;
  private final SqlitePostingReader postingReader;

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase, SqlitePostingReader postingReader) {
    this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
    this.postingReader = Objects.requireNonNull(postingReader, "postingReader");
  }

  @Override
  public boolean isInitialized() {
    try {
      return SqliteBookState.INITIALIZED_FINGRIND
          == SqliteBookContract.BOOK_STATE_READER.snapshot(activeDatabase).state();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return Optional.ofNullable(findAccounts(Set.of(accountCode)).get(accountCode));
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    try {
      return SqliteStatementQueries.findAccounts(activeDatabase, accountCodes);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
        statement -> statement.bindText(1, idempotencyKey.value()));
  }

  @Override
  public Optional<CommittedPosting> findPosting(PostingId postingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_POSTING_BY_ID, statement -> statement.bindText(1, postingId.value()));
  }

  @Override
  public Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_REVERSAL_FOR,
        statement -> statement.bindText(1, priorPostingId.value()));
  }

  private Optional<CommittedPosting> findPostingWithBinder(
      String sql, SqliteStatementQueries.Binder binder) {
    try {
      return postingReader.findOneCommittedPosting(activeDatabase, sql, binder);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }
}
