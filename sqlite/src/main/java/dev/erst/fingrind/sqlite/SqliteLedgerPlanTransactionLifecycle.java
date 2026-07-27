package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.nio.file.Files;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Owns SQLite begin, commit, and rollback state for ledger plans. */
final class SqliteLedgerPlanTransactionLifecycle {
  private final SqliteStoreContext context;
  private final SqliteLedgerPlanTransactionStateHolder state;

  SqliteLedgerPlanTransactionLifecycle(
      SqliteStoreContext context, SqliteLedgerPlanTransactionStateHolder state) {
    this.context = Objects.requireNonNull(context, "context");
    this.state = Objects.requireNonNull(state, "state");
  }

  void beginAttestedPlan(
      String planId,
      AttestationPlanOperationAuthorizer authorizer,
      Supplier<SqliteNativeDatabase> databaseSupplier) {
    context.accessMode().requireWritableMutation();
    requireInactive();
    boolean missingBookAtStart = Files.notExists(context.bookPath());
    state.replace(
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(),
            SqlitePlanAttestationState.begun(planId, authorizer),
            null));
    if (context.accessMode().defersMissingBookOpen() && missingBookAtStart) {
      return;
    }
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    try {
      Objects.requireNonNull(databaseSupplier.get(), "activeDatabase");
    } catch (RuntimeException exception) {
      reset();
      throw exception;
    }
  }

  /** Begins a credential-free plan with a stable SQLite read snapshot when a book exists. */
  void beginReadOnlyPlan(String planId, Supplier<SqliteNativeDatabase> databaseSupplier) {
    requireInactive();
    boolean missingBookAtStart = Files.notExists(context.bookPath());
    ActiveLedgerPlanTransaction activeTransaction =
        new ActiveLedgerPlanTransaction(
            new DatabaseTransactionDeferred(), SqliteReadOnlyPlanState.begun(planId), null);
    state.replace(activeTransaction);
    if (missingBookAtStart) {
      return;
    }
    try {
      Objects.requireNonNull(databaseSupplier, "databaseSupplier").get().executeStatement("begin");
      state.replace(activeTransaction.withBegunDatabase(null));
    } catch (RuntimeException exception) {
      reset();
      throw exception;
    }
  }

  void commit(Supplier<SqliteNativeDatabase> databaseSupplier) {
    ActiveLedgerPlanTransaction activeTransaction = state.requireActive();
    if (activeTransaction.planExecutionState() instanceof SqlitePlanAttestationState planState
        && !planState.completedChildren().isEmpty()
        && !planState.aggregateAppended()) {
      rollback(
          activeTransaction.begunInDatabase()
              ? Objects.requireNonNull(databaseSupplier, "databaseSupplier").get()
              : null);
      throw new IllegalStateException(
          "A ledger plan with completed child mutations must append its aggregate attestation before commit.");
    }
    if (!activeTransaction.begunInDatabase()) {
      reset();
      return;
    }
    try {
      databaseSupplier.get().executeStatement("commit");
      reset();
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to commit SQLite ledger plan transaction.", exception);
    } catch (ContractFailureException exception) {
      throw exception;
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
          "Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  void rollback(@Nullable SqliteNativeDatabase publishedDatabase) {
    if (!state.active()) {
      return;
    }
    ActiveLedgerPlanTransaction activeTransaction = state.requireActive();
    boolean rollbackDatabase = activeTransaction.begunInDatabase();
    if (rollbackDatabase && publishedDatabase != null) {
      SqliteStoreOperations.rollbackQuietly(publishedDatabase);
    }
    reset();
  }

  void beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    if (!state.active()) {
      return;
    }
    ActiveLedgerPlanTransaction activeTransaction = state.requireActive();
    if (activeTransaction.begunInDatabase()) {
      return;
    }
    SqliteAttestationEvidenceStore.@Nullable ObservedHead observedAttestationHead =
        activeTransaction.attestedPlan()
            ? SqliteAttestationEvidenceStore.observeRequired(activeDatabase)
            : null;
    activeDatabase.executeStatement("begin immediate");
    state.replace(activeTransaction.withBegunDatabase(observedAttestationHead));
    if (activeTransaction.attestedPlan()) {
      SqliteAttestationEvidenceStore.requireCurrentObservedHead(
          activeDatabase,
          SqliteLedgerPlanExecution.requireObservedAttestationHead(state.requireActive()));
    }
  }

  boolean active() {
    return state.active();
  }

  boolean begunInDatabase() {
    return state.active() && state.requireActive().begunInDatabase();
  }

  void reset() {
    state.reset();
  }

  private void requireInactive() {
    if (state.active()) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
  }
}
