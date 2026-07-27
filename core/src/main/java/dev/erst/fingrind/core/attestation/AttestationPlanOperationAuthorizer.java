package dev.erst.fingrind.core.attestation;

import java.util.Objects;

/** Custody-confined authority that signs only one final aggregate ledger-plan operation. */
public final class AttestationPlanOperationAuthorizer {
  private static final AttestationOperationKind PLAN_OPERATION =
      AttestationOperationKind.EXECUTE_PLAN;
  private final AttestationOperationAuthorizer delegate;

  /** Creates one aggregate-plan authority around the custody-confined direct-operation signer. */
  public AttestationPlanOperationAuthorizer(AttestationOperationAuthorizer delegate) {
    this.delegate = AttestationOperationAuthorizer.require(delegate);
  }

  /** Signs the one final aggregate plan operation at the SQLite compare-and-swap boundary. */
  public AttestationEvidence authorizePlan(AttestationOperationRequest request) {
    AttestationOperationRequest checkedRequest = Objects.requireNonNull(request, "request");
    if (!PLAN_OPERATION.wireToken().equals(checkedRequest.operationKind())) {
      throw new IllegalArgumentException(
          "Aggregate ledger-plan authority may sign only "
              + PLAN_OPERATION.wireToken()
              + " operations.");
    }
    return delegate.authorize(checkedRequest);
  }

  /** Preserves capability non-interchangeability: only the exact bound authority compares equal. */
  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  /** Keeps hash-based ownership checks aligned with the authorizer's identity-only equality. */
  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
