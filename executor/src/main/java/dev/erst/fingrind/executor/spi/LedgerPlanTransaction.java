package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.time.Instant;

/** Owns one atomic ledger-plan transaction boundary. */
public interface LedgerPlanTransaction {
  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();

  /** Appends the one aggregate attestation operation before committing a mutating plan. */
  default void appendPlanAttestation(
      String planId, Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    java.util.Objects.requireNonNull(planId, "planId");
    java.util.Objects.requireNonNull(recordedAt, "recordedAt");
    java.util.Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    if (attestationAuthorizer.hasChildMutations()) {
      throw new UnsupportedOperationException(
          "This ledger-plan transaction cannot append aggregate attestation evidence.");
    }
  }
}
