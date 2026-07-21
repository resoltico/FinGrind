package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Shared posting delegation defaults for SQLite capability wrappers. */
interface SqlitePostingCapabilityView
    extends SqlitePostingSession,
        SqliteReadCapabilityView,
        SqliteAttestedAdministrationMutationView {
  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.AccrualCutoffRecord>
      findAccrualCutoff(dev.erst.fingrind.contract.bookkeeping.AccrualCutoffId accrualCutoffId) {
    return SqliteReadCapabilityView.super.findAccrualCutoff(accrualCutoffId);
  }

  @Override
  default java.util.Optional<dev.erst.fingrind.executor.bookkeeping.InventoryAccountState>
      findInventoryAccountState(dev.erst.fingrind.core.AccountCode inventoryAccountCode) {
    return SqliteReadCapabilityView.super.findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default java.util.List<dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord>
      inventoryMovements(dev.erst.fingrind.core.PostingId postingId) {
    return SqliteReadCapabilityView.super.inventoryMovements(postingId);
  }

  @Override
  default List<CommittedPosting> postings(EffectiveDateRange effectiveDateRange) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().postings(effectiveDateRange);
  }

  @Override
  default Optional<LocalDate> earliestPostingEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().earliestPostingEffectiveDate();
  }

  @Override
  default Optional<LocalDate> transferredThroughEffectiveDate() {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().postingHistory().transferredThroughEffectiveDate();
  }

  @Override
  default PostingCommitResult commit(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .commit(postingDraft, postingIdGenerator, attestationAuthorizer);
  }
}
