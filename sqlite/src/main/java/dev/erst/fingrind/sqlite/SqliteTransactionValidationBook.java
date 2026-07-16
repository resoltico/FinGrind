package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.tax.DeclaredTaxRegistration;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.StoredRequestPosting;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transaction-scoped validation queries that recheck posting invariants inside SQLite writes. */
final class SqliteTransactionValidationQueries {
  final SqliteNativeDatabase activeDatabase;
  private final SqlitePostingReader postingReader;
  private final boolean operationalInitializedWorkflowGate;

  SqliteTransactionValidationQueries(
      SqliteNativeDatabase activeDatabase, SqlitePostingReader postingReader) {
    this(activeDatabase, postingReader, false);
  }

  SqliteTransactionValidationQueries(
      SqliteNativeDatabase activeDatabase,
      SqlitePostingReader postingReader,
      boolean operationalInitializedWorkflowGate) {
    this.activeDatabase = Objects.requireNonNull(activeDatabase, "activeDatabase");
    this.postingReader = Objects.requireNonNull(postingReader, "postingReader");
    this.operationalInitializedWorkflowGate = operationalInitializedWorkflowGate;
  }

  BookLifecycleInspection inspectBook() {
    try {
      SqliteBookStateSnapshot snapshot =
          SqliteBookContract.BOOK_STATE_READER.snapshot(activeDatabase);
      return SqliteBookLifecycleInspectionMapper.fromSnapshot(snapshot, activeDatabase);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  boolean allowsInitializedWorkflow() {
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

  BookIdentity requireInitializedBookIdentity() {
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

  Optional<RegisteredAccount> findAccount(AccountCode accountCode) {
    return Optional.ofNullable(findAccounts(Set.of(accountCode)).get(accountCode));
  }

  Map<AccountCode, RegisteredAccount> findAccounts(Set<AccountCode> accountCodes) {
    try {
      return SqliteAccountStatementQueries.findAccounts(activeDatabase, accountCodes);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  Optional<InventoryAccountState> findInventoryAccountState(AccountCode inventoryAccountCode) {
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Optional<RegisteredAccount> account = findAccount(inventoryAccountCode);
    if (account.isEmpty() || account.orElseThrow().unitOfMeasure() == null) {
      return Optional.empty();
    }
    try {
      return SqliteTransactionInventoryValidationLookup.findState(
          activeDatabase, account.orElseThrow(), requireInitializedBookIdentity());
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite inventory state.", exception);
    }
  }

  Optional<dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord> findAccrualCutoff(
      dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId accrualCutoffId) {
    try {
      return SqliteAccrualCutoffStatementQueries.findCutoff(activeDatabase, accrualCutoffId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite accrual cut-off.", exception);
    }
  }

  List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    Objects.requireNonNull(postingId, "postingId");
    try (SqliteNativeStatement statement =
        activeDatabase.prepare(SqliteInventoryCostingSql.LOAD_INVENTORY_MOVEMENTS_BY_POSTING_ID)) {
      statement.bindText(1, postingId.value());
      List<InventoryMovementRecord> movements = new java.util.ArrayList<>();
      while (statement.step() == SqliteNativeResultCode.code("ROW")) {
        movements.add(
            new InventoryMovementRecord(
                new AccountCode(SqlitePostingMapper.requiredText(statement, 0)),
                CanonicalTemporalText.parseLocalDate(
                    SqlitePostingMapper.requiredText(statement, 1),
                    "inventoryMovement.effectiveDate"),
                InventoryMovementKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 2)),
                statement.columnLong(3),
                statement.columnLong(4)));
      }
      return List.copyOf(movements);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to query SQLite inventory movements.", exception);
    }
  }

  Optional<DeclaredTaxRegistration> findTaxRegistration(TaxRegistrationId taxRegistrationId) {
    try {
      return SqliteTaxStatementQueries.findOneTaxRegistration(activeDatabase, taxRegistrationId);
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  Optional<StoredRequestPosting> findExistingPosting(IdempotencyKey idempotencyKey) {
    try {
      return postingReader.findOneStoredRequestPosting(
          activeDatabase,
          SqlitePostingSql.FIND_POSTING_BY_IDEMPOTENCY,
          statement -> statement.bindText(1, idempotencyKey.value()));
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to query SQLite book.", exception);
    }
  }

  Optional<CommittedPosting> findPosting(PostingId postingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_POSTING_BY_ID, statement -> statement.bindText(1, postingId.value()));
  }

  Optional<CommittedPosting> findReversalFor(PostingId priorPostingId) {
    return findPostingWithBinder(
        SqlitePostingSql.FIND_REVERSAL_FOR,
        statement -> statement.bindText(1, priorPostingId.value()));
  }

  List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
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

  Optional<LocalDate> earliestPostingEffectiveDate() {
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

  Optional<LocalDate> transferredThroughEffectiveDate() {
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

/** Posting-validation adapter over transaction-scoped SQLite query implementations. */
final class SqliteTransactionValidationBook implements SqliteTransactionValidationCapabilityView {
  private final SqliteTransactionValidationQueries validationQueries;
  private final SqliteTransactionLifecycleValidationQueries lifecycleContextQueries;
  private final SqliteTransactionValidationPayrollQueries payrollQueries;

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase, SqlitePostingReader postingReader) {
    this(activeDatabase, postingReader, false);
  }

  SqliteTransactionValidationBook(
      SqliteNativeDatabase activeDatabase,
      SqlitePostingReader postingReader,
      boolean operationalInitializedWorkflowGate) {
    validationQueries =
        new SqliteTransactionValidationQueries(
            activeDatabase, postingReader, operationalInitializedWorkflowGate);
    lifecycleContextQueries = new SqliteTransactionLifecycleValidationQueries(activeDatabase);
    payrollQueries = new SqliteTransactionValidationPayrollQueries(activeDatabase);
  }

  static SqliteTransactionValidationBook requireOwner(Object capabilityView) {
    return (SqliteTransactionValidationBook) capabilityView;
  }

  SqliteTransactionValidationQueries validationQueries() {
    return validationQueries;
  }

  SqliteTransactionLifecycleValidationQueries lifecycleContextQueries() {
    return lifecycleContextQueries;
  }

  SqliteTransactionValidationPayrollQueries payrollQueries() {
    return payrollQueries;
  }
}
