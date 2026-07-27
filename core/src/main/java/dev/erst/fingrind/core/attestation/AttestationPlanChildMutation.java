package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Immutable post-persistence child projection included in one aggregate execute-plan operation. */
public record AttestationPlanChildMutation(
    int stepOrder, String operationKind, AttestationOperationPreimages preimages) {
  /** Validates one child mutation that the plan transaction has durably completed. */
  public AttestationPlanChildMutation {
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
