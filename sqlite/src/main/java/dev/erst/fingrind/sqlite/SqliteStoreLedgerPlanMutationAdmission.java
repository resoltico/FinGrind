package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.Objects;

/** Admits direct and plan-child mutations while preserving plan rollback ownership. */
final class SqliteStoreLedgerPlanMutationAdmission {
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteLedgerPlanTransactionLifecycle transactionLifecycle;
  private final SqliteLedgerPlanExecution planExecution;

  SqliteStoreLedgerPlanMutationAdmission(
      SqliteStoreLifecycle lifecycle,
      SqliteLedgerPlanTransactionLifecycle transactionLifecycle,
      SqliteLedgerPlanExecution planExecution) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.transactionLifecycle =
        Objects.requireNonNull(transactionLifecycle, "transactionLifecycle");
    this.planExecution = Objects.requireNonNull(planExecution, "planExecution");
  }

  SqliteAttestedWriteAdmission admitDirectAttestedWrite(SqliteNativeDatabase activeDatabase) {
    lifecycle.requireOwnerThread();
    planExecution.requireDirectMutationPermitted();
    SqliteAttestationEvidenceStore.ObservedHead observedHead =
        SqliteAttestationEvidenceStore.observeRequired(activeDatabase);
    activeDatabase.executeStatement("begin immediate");
    return new SqliteAttestedWriteAdmission(observedHead, SqliteTransactionOwnership.OWNED);
  }

  SqliteAttestedWriteAdmission admitPlanChildWrite(
      SqliteNativeDatabase activeDatabase, AttestationPlanOperationAuthorizer authorizer) {
    lifecycle.requireOwnerThread();
    planExecution.requirePlanChildMutation(authorizer);
    if (!transactionLifecycle.active()) {
      throw new IllegalStateException(
          "Plan child mutations require an active aggregate-attested ledger plan.");
    }
    transactionLifecycle.beginImmediateIfNeeded(activeDatabase);
    return new SqliteAttestedWriteAdmission(
        planExecution.requireObservedAttestationHead(), SqliteTransactionOwnership.SHARED);
  }

  void recordCompletedPlanChild(
      AttestationPlanOperationAuthorizer authorizer,
      String operationKind,
      AttestationOperationPreimages preimages) {
    lifecycle.requireOwnerThread();
    planExecution.recordCompletedPlanChild(authorizer, operationKind, preimages);
  }

  void requirePlanChildMutation(AttestationPlanOperationAuthorizer authorizer) {
    lifecycle.requireOwnerThread();
    planExecution.requirePlanChildMutation(authorizer);
  }

  void requireDirectMutationPermitted() {
    lifecycle.requireOwnerThread();
    planExecution.requireDirectMutationPermitted();
  }

  void abortAttestedPlanOnChildFailure(RuntimeException failure) {
    lifecycle.requireOwnerThread();
    RuntimeException checkedFailure = Objects.requireNonNull(failure, "failure");
    if (!planExecution.activeAttestedPlan()) {
      return;
    }
    try {
      transactionLifecycle.rollback(lifecycle.publishedDatabase());
    } catch (RuntimeException rollbackFailure) {
      checkedFailure.addSuppressed(rollbackFailure);
    }
  }
}
