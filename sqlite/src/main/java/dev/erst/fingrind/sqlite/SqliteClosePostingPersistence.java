package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.attestation.AttestationClosePostingSnapshot;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPeriodCloseMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPostingLine;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy.Decision;
import dev.erst.fingrind.executor.bookkeeping.SweptInterimResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Durable persistence for generated close postings inside SQLite close workflows. */
final class SqliteClosePostingPersistence {
  private static final AttestationOperationKind INTERIM_RESULT_SWEEP_OPERATION =
      AttestationOperationKind.INTERIM_RESULT_SWEEP;
  private static final AttestationOperationKind FISCAL_YEAR_CLOSE_OPERATION =
      AttestationOperationKind.FISCAL_YEAR_CLOSE;
  private final SqliteStoreContext context;
  private final SqliteCommitFaultHook commitFaultHook;
  private final PostingAcceptancePolicy postingAcceptancePolicy;

  SqliteClosePostingPersistence(
      SqliteStoreContext context,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context = Objects.requireNonNull(context, "context");
    this.commitFaultHook = Objects.requireNonNull(commitFaultHook, "commitFaultHook");
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
  }

  CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      AcceptedPosting acceptedPosting,
      RequestFingerprint requestFingerprint,
      CommittedProvenance provenance,
      PostingIdGenerator postingIdGenerator) {
    CommittedPosting postingFact =
        acceptedPosting.materialize(
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

  AttestedInterimResultSweep persistInterimResultSweep(
      SqliteNativeDatabase activeDatabase,
      SqliteAttestationEvidenceStore.ObservedHead observedHead,
      InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer requiredAttestationAuthorizer =
        AttestationOperationAuthorizer.require(attestationAuthorizer);
    List<CommittedPosting> closingPostings =
        persistGeneratedClosePostings(
            activeDatabase,
            interimResultSweepDraft.closingPostings(),
            postingIdGenerator,
            "interim result sweep");
    SweptInterimResult sweptInterimResult =
        SqliteMutationWriter.insertInterimResultSweep(
            activeDatabase,
            interimResultSweepDraft.reportingPeriod(),
            interimResultSweepDraft.resultHoldingAccountCode(),
            interimResultSweepDraft.sweptTotals(),
            interimResultSweepDraft.sweptAt(),
            closingPostings);
    var verification =
        SqliteAttestationEvidenceStore.appendAuthorized(
            activeDatabase,
            observedHead,
            INTERIM_RESULT_SWEEP_OPERATION,
            interimResultSweepDraft.sweptAt(),
            AttestationPeriodCloseMutationProjection.projectInterimResultSweep(
                INTERIM_RESULT_SWEEP_OPERATION.wireToken(),
                interimResultSweepDraft.reportingPeriod(),
                interimResultSweepDraft.resultHoldingAccountCode().value(),
                sweptInterimResult.sweepOrder(),
                sweptInterimResult.sweptTotals(),
                closePostingSnapshots(closingPostings)),
            requiredAttestationAuthorizer);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.interimResultSwept(
            interimResultSweepDraft.sweptAt(), sweptInterimResult.sweepOrder()));
    return new AttestedInterimResultSweep(
        sweptInterimResult, AttestationCommitProjection.fromVerifiedAppend(verification));
  }

  AttestedFiscalYearClose persistFiscalYearClose(
      SqliteNativeDatabase activeDatabase,
      SqliteAttestationEvidenceStore.ObservedHead observedHead,
      FiscalYearCloseDraft closeDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer requiredAttestationAuthorizer =
        AttestationOperationAuthorizer.require(attestationAuthorizer);
    List<CommittedPosting> closePostings =
        persistGeneratedClosePostings(
            activeDatabase,
            closeDraft.closePostingDrafts(),
            postingIdGenerator,
            "fiscal year close");
    ClosedFiscalYearRecord closedFiscalYear =
        SqliteMutationWriter.insertFiscalYearClose(
            activeDatabase,
            closeDraft.reportingPeriod(),
            closeDraft.capitalAccountCode(),
            closeDraft.resultHoldingAccountCode(),
            closeDraft.retainedAccumulatedAccountCode(),
            closeDraft.closedAt(),
            closePostings);
    var verification =
        SqliteAttestationEvidenceStore.appendAuthorized(
            activeDatabase,
            observedHead,
            FISCAL_YEAR_CLOSE_OPERATION,
            closeDraft.closedAt(),
            AttestationPeriodCloseMutationProjection.projectFiscalYearClose(
                FISCAL_YEAR_CLOSE_OPERATION.wireToken(),
                closeDraft.reportingPeriod(),
                closeDraft.capitalAccountCode().value(),
                closeDraft.resultHoldingAccountCode().value(),
                closeDraft.retainedAccumulatedAccountCode().value(),
                closedFiscalYear.closeOrder(),
                closePostingSnapshots(closePostings)),
            requiredAttestationAuthorizer);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.fiscalYearClosed(
            closedFiscalYear.closedAt(), closedFiscalYear.closeOrder()));
    return new AttestedFiscalYearClose(
        closedFiscalYear, AttestationCommitProjection.fromVerifiedAppend(verification));
  }

  record AttestedInterimResultSweep(
      SweptInterimResult sweptInterimResult,
      dev.erst.fingrind.contract.bookkeeping.AttestationCommit attestationCommit) {
    AttestedInterimResultSweep {
      Objects.requireNonNull(sweptInterimResult, "sweptInterimResult");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  record AttestedFiscalYearClose(
      ClosedFiscalYearRecord closedFiscalYear,
      dev.erst.fingrind.contract.bookkeeping.AttestationCommit attestationCommit) {
    AttestedFiscalYearClose {
      Objects.requireNonNull(closedFiscalYear, "closedFiscalYear");
      Objects.requireNonNull(attestationCommit, "attestationCommit");
    }
  }

  private List<CommittedPosting> persistGeneratedClosePostings(
      SqliteNativeDatabase activeDatabase,
      List<PostingDraft> postingDrafts,
      PostingIdGenerator postingIdGenerator,
      String closeOperation) {
    SqliteTransactionValidationBook validationBook =
        new SqliteTransactionValidationBook(activeDatabase, context.postingReader(), true);
    PostingIdGenerator requiredPostingIdGenerator =
        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    List<CommittedPosting> postings = new java.util.ArrayList<>();
    for (PostingDraft postingDraft : postingDrafts) {
      switch (postingAcceptancePolicy.decisionFor(postingDraft, validationBook)) {
        case Decision.Replay replay -> postings.add(replay.postingFact());
        case Decision.Rejected rejected ->
            throw new IllegalStateException(
                "Generated "
                    + closeOperation
                    + " posting failed bookkeeping acceptance: "
                    + rejected.rejection());
        case Decision.Accepted accepted ->
            postings.add(
                persistAcceptedPosting(
                    activeDatabase,
                    accepted.acceptedPosting(),
                    accepted.requestFingerprint(),
                    postingDraft.provenance(),
                    requiredPostingIdGenerator));
      }
    }
    return postings;
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

  private static List<AttestationClosePostingSnapshot> closePostingSnapshots(
      List<CommittedPosting> postings) {
    return postings.stream().map(SqliteClosePostingPersistence::closePostingSnapshot).toList();
  }

  private static AttestationClosePostingSnapshot closePostingSnapshot(CommittedPosting posting) {
    return new AttestationClosePostingSnapshot(
        UUID.fromString(posting.postingId().value()),
        UUID.fromString(posting.provenance().requestProvenance().commandId().value()),
        posting.provenance().requestProvenance().idempotencyKey().value(),
        posting.provenance().requestProvenance().causationId().value(),
        posting.postingKind().wireValue(),
        posting.postingOriginKind().wireValue(),
        posting.journalEntry().effectiveDate(),
        posting.provenance().recordedAt(),
        posting.provenance().sourceChannel().wireValue(),
        posting.journalEntry().lines().stream()
            .map(
                line ->
                    new AttestationPostingLine(
                        line.accountCode().value(),
                        line.side().wireValue(),
                        line.amount().currencyUnit().code(),
                        line.amount().minorUnits()))
            .toList());
  }

  private static String inventoryMovementId(PostingId postingId, int movementIndex) {
    return postingId.value() + "/inventory/" + (movementIndex + 1);
  }
}
