package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.PostingValidationBook;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transaction-scoped validation view that rechecks posting invariants inside SQLite writes. */
final class SqliteTransactionValidationBook implements PostingValidationBook {
  private final SqliteNativeDatabase activeDatabase;
  private final SqlitePostingReadSupport postingReadSupport;

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase, SqlitePostingReadSupport postingReadSupport) {
    this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
    this.postingReadSupport = Objects.requireNonNull(postingReadSupport, "postingReadSupport");
  }

  @Override
  public boolean isInitialized() {
    try {
      return SqliteBookState.INITIALIZED_FINGRIND
          == SqliteBookContract.BOOK_STATE_READER.snapshot(activeDatabase).state();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    return Optional.ofNullable(findAccounts(Set.of(accountCode)).get(accountCode));
  }

  @Override
  public Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    try {
      return SqliteStatementQuerySupport.findAccounts(activeDatabase, accountCodes);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
        statement -> statement.bindText(1, idempotencyKey.value()));
  }

  @Override
  public Optional<PostingFact> findPosting(PostingId postingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_POSTING_BY_ID, statement -> statement.bindText(1, postingId.value()));
  }

  @Override
  public Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_REVERSAL_FOR,
        statement -> statement.bindText(1, priorPostingId.value()));
  }

  private Optional<PostingFact> findPostingWithBinder(
      String sql, SqliteStatementQuerySupport.Binder binder) {
    try {
      return postingReadSupport.findOnePosting(activeDatabase, sql, binder);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }
}
