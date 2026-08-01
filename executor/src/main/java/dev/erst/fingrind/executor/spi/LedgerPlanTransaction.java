package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.time.Instant;

/** Owns one atomic aggregate-attested ledger-plan transaction boundary. */
public interface LedgerPlanTransaction {
  /**
   * Begins one atomic ledger-plan transaction bound to its immutable plan identity and authority.
   */
  void beginLedgerPlanTransaction(
      String planId, AttestationPlanOperationAuthorizer attestationAuthorizer);

  /** Marks one source-plan step before it executes within the active plan transaction. */
  void enterLedgerPlanStep(int stepOrder);

  /** Commits the active ledger-plan transaction. */
  void commitLedgerPlanTransaction();

  /** Rolls back the active ledger-plan transaction. */
  void rollbackLedgerPlanTransaction();

  /** Returns whether this transaction has durably completed any aggregate-plan child mutation. */
  boolean hasCompletedLedgerPlanChildren();

  /**
   * Appends the one aggregate attestation operation before committing a mutating plan.
   *
   * <p>The transaction owns the completed child projections and must reject a read-only or
   * duplicate aggregate append. The supplied authority must be the exact authority bound at begin.
   */
  AttestationCommit appendPlanAttestation(
      Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer);
}
