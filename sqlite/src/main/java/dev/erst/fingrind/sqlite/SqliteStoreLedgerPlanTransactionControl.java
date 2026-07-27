package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.Objects;

/** Coordinates the durable database lifetime of one store-bound ledger-plan transaction. */
final class SqliteStoreLedgerPlanTransactionControl {
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteLedgerPlanTransactionLifecycle transactionLifecycle;
  private final SqliteLedgerPlanExecution planExecution;

  SqliteStoreLedgerPlanTransactionControl(
      SqliteStoreLifecycle lifecycle,
      SqliteLedgerPlanTransactionLifecycle transactionLifecycle,
      SqliteLedgerPlanExecution planExecution) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.transactionLifecycle =
        Objects.requireNonNull(transactionLifecycle, "transactionLifecycle");
    this.planExecution = Objects.requireNonNull(planExecution, "planExecution");
  }

  void beginAttestedPlan(String planId, AttestationPlanOperationAuthorizer authorizer) {
    lifecycle.ensureOpenSession();
    transactionLifecycle.beginAttestedPlan(planId, authorizer, lifecycle::database);
  }

  void beginReadOnlyPlan(String planId) {
    lifecycle.ensureOpenSession();
    transactionLifecycle.beginReadOnlyPlan(planId, lifecycle::database);
  }

  void commit() {
    lifecycle.ensureOpenSession();
    transactionLifecycle.commit(lifecycle::database);
  }

  void rollback() {
    lifecycle.requireOwnerThread();
    transactionLifecycle.rollback(lifecycle.publishedDatabase());
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    lifecycle.requireOwnerThread();
    if (transactionLifecycle.active()) {
      planExecution.requireDirectMutationPermitted();
      transactionLifecycle.beginImmediateIfNeeded(activeDatabase);
      return SqliteTransactionOwnership.SHARED;
    }
    activeDatabase.executeStatement("begin immediate");
    return SqliteTransactionOwnership.OWNED;
  }

  boolean active() {
    lifecycle.requireOwnerThread();
    return transactionLifecycle.active();
  }

  boolean begunInDatabase() {
    lifecycle.requireOwnerThread();
    return transactionLifecycle.begunInDatabase();
  }
}
