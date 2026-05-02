package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Point-query and inspection reads over one SQLite-backed book session. */
final class SqliteStoreQueryOperations {
  /** One initialized-book point query executed against a live SQLite handle. */
  @FunctionalInterface
  private interface NativeQuery<T> {
    /** Runs one point query against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;

  SqliteStoreQueryOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  BookInspection inspectBook() {
    lifecycle.ensureOpenSession();
    if (Files.notExists(context.bookPath())) {
      return new BookInspection.Missing(SqliteBookContract.FORMAT_VERSION);
    }
    try {
      SqliteNativeDatabase activeDatabase = lifecycle.database();
      SqliteBookStateSnapshot snapshot = lifecycle.stateSnapshot(activeDatabase);
      return switch (snapshot.state()) {
        case BLANK_SQLITE ->
            new BookInspection.Existing(
                BookInspection.Status.BLANK_SQLITE,
                snapshot.applicationId(),
                snapshot.userVersion(),
                SqliteBookContract.FORMAT_VERSION);
        case INITIALIZED_FINGRIND ->
            new BookInspection.Initialized(
                snapshot.applicationId(),
                snapshot.userVersion(),
                SqliteBookContract.FORMAT_VERSION,
                SqliteStatementQueries.loadInitializedAt(activeDatabase).orElseThrow());
        case FOREIGN_SQLITE ->
            new BookInspection.Existing(
                BookInspection.Status.FOREIGN_SQLITE,
                snapshot.applicationId(),
                snapshot.userVersion(),
                SqliteBookContract.FORMAT_VERSION);
        case UNSUPPORTED_FINGRIND_VERSION ->
            new BookInspection.Existing(
                BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
                snapshot.applicationId(),
                snapshot.userVersion(),
                SqliteBookContract.FORMAT_VERSION);
        case INCOMPLETE_FINGRIND ->
            new BookInspection.Existing(
                BookInspection.Status.INCOMPLETE_FINGRIND,
                snapshot.applicationId(),
                snapshot.userVersion(),
                SqliteBookContract.FORMAT_VERSION);
      };
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to inspect SQLite book.", exception);
    }
  }

  boolean isInitialized() {
    lifecycle.ensureOpenSession();
    if (Files.notExists(context.bookPath())) {
      return false;
    }
    try {
      SqliteBookStateSnapshot snapshot = lifecycle.stateSnapshot(lifecycle.database());
      return switch (snapshot.state()) {
        case BLANK_SQLITE -> false;
        case INITIALIZED_FINGRIND -> true;
        case FOREIGN_SQLITE -> throw SqliteStoreOperations.foreignBookFailure();
        case UNSUPPORTED_FINGRIND_VERSION ->
            throw SqliteStoreOperations.unsupportedBookVersionFailure(
                snapshot.userVersion(), SqliteBookContract.FORMAT_VERSION);
        case INCOMPLETE_FINGRIND -> throw SqliteStoreOperations.incompleteBookFailure();
      };
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  Optional<DeclaredAccount> findAccount(AccountCode accountCode) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteStatementQueries.findOneAccount(activeDatabase, accountCode));
  }

  Map<AccountCode, DeclaredAccount> findAccounts(Set<AccountCode> accountCodes) {
    lifecycle.ensureOpenSession();
    Set<AccountCode> requestedAccounts =
        new LinkedHashSet<>(Objects.requireNonNull(accountCodes, "accountCodes"));
    if (requestedAccounts.isEmpty()) {
      return Map.of();
    }
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteStatementQueries.findAccounts(activeDatabase, requestedAccounts));
  }

  AccountPage listAccounts(ListAccountsQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteStatementQueries.loadAccountPage(activeDatabase, query));
  }

  Optional<PostingFact> findExistingPosting(IdempotencyKey idempotencyKey) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOnePosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
                    statement -> statement.bindText(1, idempotencyKey.value())));
  }

  Optional<PostingFact> findPosting(PostingId postingId) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOnePosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_ID,
                    statement -> statement.bindText(1, postingId.value())));
  }

  Optional<PostingFact> findReversalFor(PostingId priorPostingId) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOnePosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_REVERSAL_FOR,
                    statement -> statement.bindText(1, priorPostingId.value())));
  }

  PostingPage listPostings(ListPostingsQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> context.postingReader().loadPostingPage(activeDatabase, query));
  }

  private <T> T queryInitialized(String failureMessage, NativeQuery<T> query) {
    try {
      return query.run(lifecycle.initializedQueryDatabase());
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
