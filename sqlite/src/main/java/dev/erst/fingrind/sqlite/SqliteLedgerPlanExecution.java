package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.Objects;

/** Owns attested-plan progression and aggregate-attestation invariants for one transaction. */
final class SqliteLedgerPlanExecution {
  private final SqliteLedgerPlanTransactionStateHolder state;

  SqliteLedgerPlanExecution(SqliteLedgerPlanTransactionStateHolder state) {
    this.state = Objects.requireNonNull(state, "state");
  }

  boolean activeAttestedPlan() {
    return state.active() && state.requireActive().attestedPlan();
  }

  SqliteAttestationEvidenceStore.ObservedHead requireObservedAttestationHead() {
    return requireObservedAttestationHead(state.requireActive());
  }

  static SqliteAttestationEvidenceStore.ObservedHead requireObservedAttestationHead(
      ActiveLedgerPlanTransaction activeTransaction) {
    Objects.requireNonNull(activeTransaction, "activeTransaction");
    if (!activeTransaction.attestedPlan()) {
      throw new IllegalStateException(
          "Only an attested ledger plan may use an aggregate attestation head.");
    }
    if (activeTransaction.observedAttestationHead() == null) {
      throw new IllegalStateException(
          "A mutating ledger plan must observe its attestation head before write admission.");
    }
    return activeTransaction.observedAttestationHead();
  }

  void enterPlanStep(int stepOrder) {
    ActiveLedgerPlanTransaction activeTransaction = state.requireActive();
    state.replace(
        activeTransaction.withPlanExecutionState(
            activeTransaction.planExecutionState().enterStep(stepOrder)));
  }

  void recordCompletedPlanChild(
      AttestationPlanOperationAuthorizer authorizer,
      String operationKind,
      AttestationOperationPreimages preimages) {
    ActiveLedgerPlanTransaction activeTransaction = requireAttestedPlan();
    SqlitePlanAttestationState planState = activeTransaction.requireAttestationState();
    state.replace(
        activeTransaction.withPlanExecutionState(
            planState.completeChild(authorizer, operationKind, preimages)));
  }

  void requirePlanChildMutation(AttestationPlanOperationAuthorizer authorizer) {
    planAttestationState(authorizer).requireChildMayComplete(authorizer);
  }

  void requireDirectMutationPermitted() {
    if (state.active()) {
      throw new IllegalStateException(
          "Direct mutations cannot run inside an active ledger-plan transaction.");
    }
  }

  boolean hasCompletedPlanChildren() {
    return state.active()
        && state.requireActive().attestedPlan()
        && !state.requireActive().requireAttestationState().completedChildren().isEmpty();
  }

  AttestationOperationPreimages aggregatePreimages(AttestationPlanOperationAuthorizer authorizer) {
    SqlitePlanAttestationState planState = planAttestationState(authorizer);
    if (planState.aggregateAppended()) {
      throw new IllegalStateException(
          "A ledger plan may append its aggregate attestation exactly once.");
    }
    if (planState.completedChildren().isEmpty()) {
      throw new IllegalArgumentException(
          "A ledger plan without completed child mutations must not request an aggregate attestation append.");
    }
    return AttestationPlanMutationProjection.project(
        planState.planId(), planState.completedChildren());
  }

  void markAggregateAppended(AttestationPlanOperationAuthorizer authorizer) {
    ActiveLedgerPlanTransaction activeTransaction = requireAttestedPlan();
    SqlitePlanAttestationState planState = activeTransaction.requireAttestationState();
    state.replace(activeTransaction.withPlanExecutionState(planState.appendAggregate(authorizer)));
  }

  private ActiveLedgerPlanTransaction requireAttestedPlan() {
    ActiveLedgerPlanTransaction activeTransaction = state.requireActive();
    activeTransaction.requireAttestationState();
    return activeTransaction;
  }

  private SqlitePlanAttestationState planAttestationState(
      AttestationPlanOperationAuthorizer authorizer) {
    SqlitePlanAttestationState planState = requireAttestedPlan().requireAttestationState();
    planState.requireSameAuthorizer(authorizer);
    return planState;
  }
}
