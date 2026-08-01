package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationOperationPreimages;
import dev.erst.fingrind.core.attestation.AttestationPlanChildMutation;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Internal ledger-plan transaction state model for one SQLite store lifecycle instance. */
sealed interface LedgerPlanTransactionState
    permits NoLedgerPlanTransaction, ActiveLedgerPlanTransaction {}

/** Lifecycle state when no ledger-plan transaction is active. */
record NoLedgerPlanTransaction() implements LedgerPlanTransactionState {}

/** Shared source-plan state for one active attested or credential-free plan execution. */
sealed interface SqlitePlanExecutionState
    permits SqlitePlanAttestationState, SqliteReadOnlyPlanState {
  /** Advances this plan to one strictly later source step. */
  SqlitePlanExecutionState enterStep(int stepOrder);
}

/** Active ledger-plan transaction with explicit database and source-plan state. */
record ActiveLedgerPlanTransaction(
    DatabaseTransactionState databaseTransactionState,
    SqlitePlanExecutionState planExecutionState,
    SqliteAttestationEvidenceStore.@Nullable ObservedHead observedAttestationHead)
    implements LedgerPlanTransactionState {
  ActiveLedgerPlanTransaction {
    Objects.requireNonNull(databaseTransactionState, "databaseTransactionState");
    Objects.requireNonNull(planExecutionState, "planExecutionState");
  }

  boolean begunInDatabase() {
    return databaseTransactionState instanceof DatabaseTransactionBegun;
  }

  boolean attestedPlan() {
    return planExecutionState instanceof SqlitePlanAttestationState;
  }

  boolean readOnlyPlan() {
    return planExecutionState instanceof SqliteReadOnlyPlanState;
  }

  SqlitePlanAttestationState requireAttestationState() {
    if (planExecutionState instanceof SqlitePlanAttestationState state) {
      return state;
    }
    throw new IllegalStateException(
        "Plan child mutations and aggregate attestation require an aggregate-attested ledger plan.");
  }

  ActiveLedgerPlanTransaction withBegunDatabase(
      SqliteAttestationEvidenceStore.@Nullable ObservedHead observedAttestationHead) {
    return new ActiveLedgerPlanTransaction(
        new DatabaseTransactionBegun(), planExecutionState, observedAttestationHead);
  }

  ActiveLedgerPlanTransaction withPlanExecutionState(SqlitePlanExecutionState planExecutionState) {
    return new ActiveLedgerPlanTransaction(
        databaseTransactionState,
        Objects.requireNonNull(planExecutionState, "planExecutionState"),
        observedAttestationHead);
  }
}

/** Coordinator-owned aggregate-plan identity and completed post-persistence child projections. */
record SqlitePlanAttestationState(
    String planId,
    AttestationPlanOperationAuthorizer authorizer,
    int activeStepOrder,
    List<AttestationPlanChildMutation> completedChildren,
    boolean aggregateAppended)
    implements SqlitePlanExecutionState {
  SqlitePlanAttestationState {
    Objects.requireNonNull(planId, "planId");
    if (planId.isBlank()) {
      throw new IllegalArgumentException("planId must not be blank.");
    }
    Objects.requireNonNull(authorizer, "authorizer");
    if (activeStepOrder < -1) {
      throw new IllegalArgumentException("activeStepOrder must not be less than -1.");
    }
    completedChildren = List.copyOf(Objects.requireNonNull(completedChildren, "completedChildren"));
  }

  static SqlitePlanAttestationState begun(
      String planId, AttestationPlanOperationAuthorizer authorizer) {
    return new SqlitePlanAttestationState(planId, authorizer, -1, List.of(), false);
  }

  @Override
  public SqlitePlanAttestationState enterStep(int stepOrder) {
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan cannot execute child steps after its aggregate attestation append.");
    }
    if (stepOrder < 0) {
      throw new IllegalArgumentException("stepOrder must not be negative.");
    }
    if (stepOrder <= activeStepOrder) {
      throw new IllegalStateException("Ledger-plan steps must execute in strict source order.");
    }
    return new SqlitePlanAttestationState(
        planId, authorizer, stepOrder, completedChildren, aggregateAppended);
  }

  SqlitePlanAttestationState completeChild(
      AttestationPlanOperationAuthorizer authorizer,
      String operationKind,
      AttestationOperationPreimages preimages) {
    requireChildMayComplete(authorizer);
    List<AttestationPlanChildMutation> children = new ArrayList<>(completedChildren);
    children.add(new AttestationPlanChildMutation(activeStepOrder, operationKind, preimages));
    return new SqlitePlanAttestationState(
        planId, this.authorizer, activeStepOrder, children, aggregateAppended);
  }

  void requireChildMayComplete(AttestationPlanOperationAuthorizer authorizer) {
    requireSameAuthorizer(authorizer);
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan cannot record child mutations after its aggregate attestation append.");
    }
    if (activeStepOrder < 0) {
      throw new IllegalStateException(
          "A ledger-plan child mutation requires an active source-plan step.");
    }
    if (!completedChildren.isEmpty()
        && completedChildren.getLast().stepOrder() >= activeStepOrder) {
      throw new IllegalStateException(
          "A ledger-plan step may complete at most one attested child mutation.");
    }
  }

  SqlitePlanAttestationState appendAggregate(AttestationPlanOperationAuthorizer authorizer) {
    requireSameAuthorizer(authorizer);
    if (completedChildren.isEmpty()) {
      throw new IllegalArgumentException(
          "A ledger plan without completed child mutations must not request an aggregate attestation append.");
    }
    if (aggregateAppended) {
      throw new IllegalStateException(
          "A ledger plan may append its aggregate attestation exactly once.");
    }
    return new SqlitePlanAttestationState(
        planId, this.authorizer, activeStepOrder, completedChildren, true);
  }

  void requireSameAuthorizer(AttestationPlanOperationAuthorizer candidate) {
    if (!authorizer.equals(Objects.requireNonNull(candidate, "authorizer"))) {
      throw new IllegalArgumentException(
          "The supplied aggregate plan authority does not own this ledger-plan transaction.");
    }
  }
}

/** Source-plan state for a credential-free execution that must not mutate the protected book. */
record SqliteReadOnlyPlanState(String planId, int activeStepOrder)
    implements SqlitePlanExecutionState {
  SqliteReadOnlyPlanState {
    Objects.requireNonNull(planId, "planId");
    if (planId.isBlank()) {
      throw new IllegalArgumentException("planId must not be blank.");
    }
    if (activeStepOrder < -1) {
      throw new IllegalArgumentException("activeStepOrder must not be less than -1.");
    }
  }

  static SqliteReadOnlyPlanState begun(String planId) {
    return new SqliteReadOnlyPlanState(planId, -1);
  }

  @Override
  public SqliteReadOnlyPlanState enterStep(int stepOrder) {
    if (stepOrder < 0) {
      throw new IllegalArgumentException("stepOrder must not be negative.");
    }
    if (stepOrder <= activeStepOrder) {
      throw new IllegalStateException("Ledger-plan steps must execute in strict source order.");
    }
    return new SqliteReadOnlyPlanState(planId, stepOrder);
  }
}

/** Database-begin state for one active ledger-plan transaction. */
sealed interface DatabaseTransactionState
    permits DatabaseTransactionDeferred, DatabaseTransactionBegun {}

/** Active ledger-plan transaction before the SQLite database transaction begins. */
record DatabaseTransactionDeferred() implements DatabaseTransactionState {}

/** Active ledger-plan transaction after the SQLite database transaction begins. */
record DatabaseTransactionBegun() implements DatabaseTransactionState {}
