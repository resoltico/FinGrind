package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Owns one atomic ledger-plan transaction boundary. */
public interface LedgerPlanTransaction {
  /** Begins one atomic ledger-plan transaction. */
  void beginLedgerPlanTransaction();

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();

  /**
   * Appends the one aggregate attestation operation before committing a mutating plan.
   *
   * @return the appended operation commitment, or {@code null} when the successful plan had no
   *     child mutations
   */
  default @Nullable AttestationCommit appendPlanAttestation(
      String planId, Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    java.util.Objects.requireNonNull(planId, "planId");
    java.util.Objects.requireNonNull(recordedAt, "recordedAt");
    java.util.Objects.requireNonNull(attestationAuthorizer, "attestationAuthorizer");
    if (attestationAuthorizer.hasChildMutations()) {
      throw new UnsupportedOperationException(
          "This ledger-plan transaction cannot append aggregate attestation evidence.");
    }
    return null;
  }
}
