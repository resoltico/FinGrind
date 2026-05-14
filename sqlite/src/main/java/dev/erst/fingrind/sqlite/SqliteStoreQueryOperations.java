package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountCurrencyTotals;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryPage;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryPage;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
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

  BookLifecycleInspection inspectBook() {
    lifecycle.ensureOpenSession();
    if (Files.notExists(context.bookPath())) {
      return SqliteBookLifecycleInspectionMapper.fromMissingPath();
    }
    try {
      SqliteNativeDatabase activeDatabase = lifecycle.database();
      SqliteBookStateSnapshot snapshot = lifecycle.stateSnapshot(activeDatabase);
      return SqliteBookLifecycleInspectionMapper.fromSnapshot(snapshot, activeDatabase);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to inspect SQLite book.", exception);
    }
  }

  Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteStatementQueries.findOneAccount(activeDatabase, accountCode));
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
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

  List<RegisteredAccount> allAccounts() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadAllAccounts(
                activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS));
  }

  AccountRegistryPage listAccounts(AccountRegistryQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> SqliteStatementQueries.loadAccountPage(activeDatabase, query));
  }

  Optional<CommittedPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneCommittedPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
                    statement -> statement.bindText(1, idempotencyKey.value())));
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneCommittedPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_POSTING_BY_ID,
                    statement -> statement.bindText(1, postingId.value())));
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .findOneCommittedPosting(
                    activeDatabase,
                    SqlitePostingSql.FIND_REVERSAL_FOR,
                    statement -> statement.bindText(1, priorPostingId.value())));
  }

  PostingHistoryPage listPostings(PostingHistoryQuery query) {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase -> context.postingReader().loadPostingPage(activeDatabase, query));
  }

  List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    lifecycle.ensureOpenSession();
    EffectiveDateRange range = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .loadCommittedPostings(
                    activeDatabase,
                    SqlitePostingSql.LOAD_POSTINGS_IN_RANGE,
                    statement -> {
                      String effectiveDateFrom =
                          range.effectiveDateFrom().map(LocalDate::toString).orElse(null);
                      String effectiveDateTo =
                          range.effectiveDateTo().map(LocalDate::toString).orElse(null);
                      statement.bindText(1, effectiveDateFrom);
                      statement.bindText(2, effectiveDateFrom);
                      statement.bindText(3, effectiveDateTo);
                      statement.bindText(4, effectiveDateTo);
                    }));
  }

  Optional<LocalDate> earliestPostingEffectiveDate() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadOptionalText(
                    activeDatabase,
                    SqlitePostingSql.FIND_EARLIEST_POSTING_EFFECTIVE_DATE,
                    statement -> {})
                .map(LocalDate::parse));
  }

  Optional<LocalDate> closedThroughEffectiveDate() {
    lifecycle.ensureOpenSession();
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            SqliteStatementQueries.loadOptionalText(
                    activeDatabase,
                    SqlitePostingSql.FIND_CLOSED_THROUGH_EFFECTIVE_DATE,
                    statement -> {})
                .map(LocalDate::parse));
  }

  List<AccountCurrencyTotals> accountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    lifecycle.ensureOpenSession();
    EffectiveDateRange range = Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    PostingCoverage requiredPostingCoverage =
        Objects.requireNonNull(postingCoverage, "postingCoverage");
    return queryInitialized(
        "Failed to query SQLite book.",
        activeDatabase ->
            context
                .postingReader()
                .loadAccountTotals(activeDatabase, range, requiredPostingCoverage));
  }

  private <T> T queryInitialized(String failureMessage, NativeQuery<T> query) {
    try {
      return query.run(lifecycle.initializedQueryDatabase());
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(failureMessage, exception);
    }
  }
}
