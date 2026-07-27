package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.Objects;

/** Exposes attested-plan progression and aggregate-attestation state at the store boundary. */
final class SqliteStoreLedgerPlanExecutionControl {
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteLedgerPlanExecution planExecution;

  SqliteStoreLedgerPlanExecutionControl(
      SqliteStoreLifecycle lifecycle, SqliteLedgerPlanExecution planExecution) {
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.planExecution = Objects.requireNonNull(planExecution, "planExecution");
  }

  SqliteAttestationEvidenceStore.ObservedHead requireObservedAttestationHead() {
    lifecycle.requireOwnerThread();
    return planExecution.requireObservedAttestationHead();
  }

  void enterPlanStep(int stepOrder) {
    lifecycle.requireOwnerThread();
    planExecution.enterPlanStep(stepOrder);
  }

  boolean hasCompletedPlanChildren() {
    lifecycle.requireOwnerThread();
    return planExecution.hasCompletedPlanChildren();
  }

  AttestationOperationPreimages aggregatePreimages(AttestationPlanOperationAuthorizer authorizer) {
    lifecycle.requireOwnerThread();
    return planExecution.aggregatePreimages(authorizer);
  }

  void markAggregateAppended(AttestationPlanOperationAuthorizer authorizer) {
    lifecycle.requireOwnerThread();
    planExecution.markAggregateAppended(authorizer);
  }
}
