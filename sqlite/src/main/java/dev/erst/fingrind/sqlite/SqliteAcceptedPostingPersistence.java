package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.Objects;

/** Persists one accepted posting and every durable fact that belongs to that posting. */
final class SqliteAcceptedPostingPersistence {
  private final SqliteCommitFaultHook commitFaultHook;

  SqliteAcceptedPostingPersistence(SqliteCommitFaultHook commitFaultHook) {
    this.commitFaultHook = Objects.requireNonNull(commitFaultHook, "commitFaultHook");
  }

  CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      AcceptedPosting acceptedPosting,
      RequestFingerprint requestFingerprint,
      CommittedProvenance provenance,
      PostingIdGenerator postingIdGenerator) {
    CommittedPosting postingFact =
        Objects.requireNonNull(acceptedPosting, "acceptedPosting")
            .materialize(
                Objects.requireNonNull(postingIdGenerator, "postingIdGenerator").nextPostingId(),
                Objects.requireNonNull(provenance, "provenance"));
    persistMaterializedPosting(activeDatabase, acceptedPosting, postingFact, requestFingerprint);
    return postingFact;
  }

  void persistMaterializedPosting(
      SqliteNativeDatabase activeDatabase,
      AcceptedPosting acceptedPosting,
      CommittedPosting postingFact,
      RequestFingerprint requestFingerprint) {
    SqliteMutationWriter.insertPostingFact(
        activeDatabase,
        Objects.requireNonNull(postingFact, "postingFact"),
        Objects.requireNonNull(requestFingerprint, "requestFingerprint"));
    commitFaultHook.afterPostingFactInserted(postingFact);
    SqliteMutationWriter.insertJournalLines(activeDatabase, postingFact, commitFaultHook);
    SqliteAccrualCutoffWriter.persist(activeDatabase, postingFact);
    SqliteLatvianPayrollWriter.persist(activeDatabase, postingFact);
    SqliteOwnedContextWriter.persist(activeDatabase, postingFact);
    persistInventoryCosting(activeDatabase, postingFact.postingId(), acceptedPosting);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase, BookAuditEvent.postingCommitted(postingFact));
  }

  private static void persistInventoryCosting(
      SqliteNativeDatabase activeDatabase, PostingId postingId, AcceptedPosting acceptedPosting) {
    Objects.requireNonNull(activeDatabase, "activeDatabase");
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(acceptedPosting, "acceptedPosting");
    for (int index = 0; index < acceptedPosting.inventoryMovements().size(); index++) {
      var movement = acceptedPosting.inventoryMovements().get(index);
      SqliteInventoryCostingWriter.insertInventoryMovement(
          activeDatabase,
          inventoryMovementId(postingId, index),
          movement.inventoryAccount(),
          movement.effectiveDate(),
          movement.kind(),
          movement.quantityDelta(),
          movement.costDeltaMinor(),
          postingId);
    }
    acceptedPosting
        .resultingInventoryStates()
        .forEach(
            (inventoryAccount, state) ->
                SqliteInventoryCostingWriter.upsertInventoryOnHand(
                    activeDatabase,
                    inventoryAccount,
                    state.pool().quantityOnHand().scaledUnits(),
                    state.pool().costPool().minorUnits(),
                    state
                        .lastMovementDate()
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "Inventory state persisted after movement must own one last movement date."))));
  }

  private static String inventoryMovementId(PostingId postingId, int movementIndex) {
    return postingId.value() + "/inventory/" + (movementIndex + 1);
  }
}
