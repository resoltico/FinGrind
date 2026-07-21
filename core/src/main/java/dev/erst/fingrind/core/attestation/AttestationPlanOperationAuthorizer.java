package dev.erst.fingrind.core.attestation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Collects one plan's child mutation projections for a single final authorization. */
public final class AttestationPlanOperationAuthorizer implements AttestationOperationAuthorizer {
  /** Immutable child mutation projection bound to its source-plan position. */
  record ChildMutation(
      int stepOrder, String operationKind, AttestationOperationPreimages preimages) {
    ChildMutation {
      if (stepOrder < 0) {
        throw new IllegalArgumentException("stepOrder must not be negative.");
      }
      Objects.requireNonNull(operationKind, "operationKind");
      if (operationKind.isBlank()) {
        throw new IllegalArgumentException("operationKind must not be blank.");
      }
      Objects.requireNonNull(preimages, "preimages");
    }
  }

  private final AttestationOperationAuthorizer delegate;
  private final List<ChildMutation> childMutations = new ArrayList<>();
  private int activeStepOrder = -1;

  /** Creates one collector around the custody-confined signer for a single plan transaction. */
  public AttestationPlanOperationAuthorizer(AttestationOperationAuthorizer delegate) {
    this.delegate = AttestationOperationAuthorizer.require(delegate);
  }

  /** Marks the source-plan position whose mutation projection may be collected next. */
  public void enterStep(int stepOrder) {
    if (stepOrder < 0) {
      throw new IllegalArgumentException("stepOrder must not be negative.");
    }
    if (stepOrder <= activeStepOrder) {
      throw new IllegalStateException("Ledger-plan steps must be collected in source order.");
    }
    activeStepOrder = stepOrder;
  }

  /** Records one actual child mutation without authorizing an independent chain operation. */
  public void collectChildMutation(String operationKind, AttestationOperationPreimages preimages) {
    if (activeStepOrder < 0) {
      throw new IllegalStateException("A ledger-plan child mutation requires an active step.");
    }
    childMutations.add(new ChildMutation(activeStepOrder, operationKind, preimages));
  }

  /** Returns whether this plan changed protected-book state. */
  public boolean hasChildMutations() {
    return !childMutations.isEmpty();
  }

  /** Returns the immutable aggregate preimages for the final execute-plan operation. */
  public AttestationOperationPreimages planPreimages(String planId) {
    return AttestationPlanMutationProjection.project(planId, childMutations);
  }

  /** Signs the one final aggregate plan operation at the SQLite compare-and-swap boundary. */
  public AttestationEvidence authorizePlan(AttestationOperationRequest request) {
    return delegate.authorize(Objects.requireNonNull(request, "request"));
  }

  /**
   * Rejects accidental use of the aggregate collector as an independently appended operation.
   *
   * <p>The SQLite evidence boundary recognizes this type and calls {@link #collectChildMutation}
   * for child writes, then {@link #authorizePlan} exactly once after the plan succeeds.
   */
  @Override
  public AttestationEvidence authorize(AttestationOperationRequest request) {
    Objects.requireNonNull(request, "request");
    throw new IllegalStateException(
        "Ledger-plan child mutations must be finalized as one execute-plan operation.");
  }
}
