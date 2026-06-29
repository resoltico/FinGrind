package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transaction-scoped validation view that rechecks posting invariants inside SQLite writes. */
final class SqliteTransactionValidationBook implements PostingValidationStore {
  private final SqliteNativeDatabase activeDatabase;
  private final SqlitePostingReader postingReader;
  private final boolean operationalInitializedWorkflowGate;

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase, SqlitePostingReader postingReader) {
    this(activeDatabase, postingReader, false);
  }

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase,
      SqlitePostingReader postingReader,
      boolean operationalInitializedWorkflowGate) {
    this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
    this.postingReader = Objects.requireNonNull(postingReader, "postingReader");
    this.operationalInitializedWorkflowGate = operationalInitializedWorkflowGate;
  }

  @Override
  public BookLifecycleInspection inspectBook() {
    try {
      SqliteBookStateSnapshot snapshot =
          SqliteBookContract.BOOK_STATE_READER.snapshot(activeDatabase);
      return SqliteBookLifecycleInspectionMapper.fromSnapshot(snapshot, activeDatabase);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public boolean allowsInitializedWorkflow() {
    try {
      SqliteBookStateSnapshot snapshot =
          SqliteStoreOperations.retryTransientLockFailures(
              () ->
                  operationalInitializedWorkflowGate
                      ? SqliteBookContract.BOOK_STATE_READER.operationalSnapshot(activeDatabase)
                      : SqliteBookContract.BOOK_STATE_READER.snapshot(activeDatabase));
      if (snapshot.state() == SqliteBookState.BLANK_SQLITE) {
        return false;
      }
      snapshot
          .state()
          .requireInitialized(
              snapshot.userVersion(),
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.NOT_INITIALIZED_BOOK_MESSAGE);
      return true;
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to access SQLite book.", exception);
    }
  }

  @Override
  public BookIdentity requireInitializedBookIdentity() {
    try {
      return SqliteStoreOperations.retryTransientLockFailures(
          () ->
              SqliteStatementQueries.loadBookIdentity(activeDatabase)
                  .orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Initialized SQLite book is missing book identity.")));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to access SQLite book.", exception);
    }
  }

  @Override
  public Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return Optional.ofNullable(findAccounts(Set.of(accountCode)).get(accountCode));
  }

  @Override
  public Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    try {
      return SqliteAccountStatementQueries.findAccounts(activeDatabase, accountCodes);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<DeclaredTaxRegistration> findTaxRegistration(
      TaxRegistrationId taxRegistrationId) {
    try {
      return SqliteTaxStatementQueries.findOneTaxRegistration(activeDatabase, taxRegistrationId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    try {
      return postingReader.findOneStoredRequestPosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
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

  @Override
  public List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    Objects.requireNonNull(effectiveDateRange, "effectiveDateRange");
    try {
      return postingReader.loadCommittedPostings(
          activeDatabase,
          SqlitePostingSql.LOAD_POSTINGS_IN_RANGE,
          statement -> {
            String effectiveDateFrom =
                effectiveDateRange.effectiveDateFrom().map(LocalDate::toString).orElse(null);
            String effectiveDateTo =
                effectiveDateRange.effectiveDateTo().map(LocalDate::toString).orElse(null);
            statement.bindText(1, effectiveDateFrom);
            statement.bindText(2, effectiveDateFrom);
            statement.bindText(3, effectiveDateTo);
            statement.bindText(4, effectiveDateTo);
          });
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<LocalDate> earliestPostingEffectiveDate() {
    try {
      return SqliteStatementQueries.loadOptionalText(
              activeDatabase,
              SqliteReportingPeriodCloseSql.FIND_EARLIEST_POSTING_EFFECTIVE_DATE,
              statement -> {})
          .map(text -> CanonicalTemporalText.parseLocalDate(text, "postingFact.effectiveDate"));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  @Override
  public Optional<LocalDate> transferredThroughEffectiveDate() {
    try {
      return SqliteStatementQueries.loadOptionalText(
              activeDatabase,
              SqliteReportingPeriodCloseSql.FIND_CLOSED_THROUGH_EFFECTIVE_DATE,
              statement -> {})
          .map(
              text ->
                  CanonicalTemporalText.parseLocalDate(text, "interimResultSweep.effectiveDateTo"));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  private Optional<CommittedPosting> findPostingWithBinder(
      String sql, SqliteStatementBinder binder) {
    try {
      return postingReader.findOneCommittedPosting(activeDatabase, sql, binder);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }
}
