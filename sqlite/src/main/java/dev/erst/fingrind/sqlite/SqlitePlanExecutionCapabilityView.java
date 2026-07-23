package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.AttestationCommit;
import dev.erst.fingrind.core.attestation.AttestationPlanOperationAuthorizer;
import java.time.Instant;
import java.util.HexFormat;
import org.jspecify.annotations.Nullable;

/** Shared ledger-plan transaction defaults for SQLite capability wrappers. */
interface SqlitePlanExecutionCapabilityView
    extends SqlitePlanExecutionSession, SqlitePostingCapabilityView {
  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.InventoryAccountState>
      findInventoryAccountState(dev.erst.fingrind.core.AccountCode inventoryAccountCode) {
    return SqlitePostingCapabilityView.super.findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord>
      inventoryMovements(dev.erst.fingrind.core.PostingId postingId) {
    return SqlitePostingCapabilityView.super.inventoryMovements(postingId);
  }

  @Override
  default void beginLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().beginAttestedPlan();
  }

  @Override
  default void commitLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().commit();
  }

  @Override
  default void rollbackLedgerPlanTransaction() {
    storeThreadOwner().requireOwnerThread();
    storeLifecycle().transactions().rollback();
  }

  @Override
  default @Nullable AttestationCommit appendPlanAttestation(
      String planId, Instant recordedAt, AttestationPlanOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    if (!attestationAuthorizer.hasChildMutations()) {
      return null;
    }
    dev.erst.fingrind.core.attestation.AttestationVerification verification =
        SqliteAttestationEvidenceStore.appendPlanAuthorized(
            storeLifecycle().database(),
            storeLifecycle().transactions().requireObservedAttestationHead(),
            planId,
            recordedAt,
            attestationAuthorizer);
    return new AttestationCommit(
        verification.headOrder(), HexFormat.of().formatHex(verification.operationHead()));
  }
}
