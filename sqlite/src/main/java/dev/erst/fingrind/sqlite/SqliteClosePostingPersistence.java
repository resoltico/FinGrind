package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationClosePostingSnapshot;
import dev.erst.fingrind.core.attestation.AttestationInterimResultSweepEffect;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.core.attestation.AttestationOperationKind;
import dev.erst.fingrind.core.attestation.AttestationPeriodCloseMutationProjection;
import dev.erst.fingrind.core.attestation.AttestationPostingLine;
import dev.erst.fingrind.executor.AttestationCommitProjection;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RecordedInterimResultSweep;
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
  private final SqliteGeneratedClosePostingPersistence generatedClosePostings;

  SqliteClosePostingPersistence(
      SqliteStoreContext context,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    SqliteAcceptedPostingPersistence acceptedPostings =
        new SqliteAcceptedPostingPersistence(commitFaultHook);
    generatedClosePostings =
        new SqliteGeneratedClosePostingPersistence(
            context, postingAcceptancePolicy, acceptedPostings);
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
        generatedClosePostings.persistGeneratedPostings(
            activeDatabase,
            interimResultSweepDraft.closingPostings(),
            postingIdGenerator,
            "interim result sweep");
    RecordedInterimResultSweep sweptInterimResult =
        SqliteMutationWriter.insertInterimResultSweep(
            activeDatabase,
            interimResultSweepDraft.reportingPeriod(),
            interimResultSweepDraft.resultHoldingAccountCode(),
            interimResultSweepDraft.sweptTotals(),
            interimResultSweepDraft.sweptAt(),
            closingPostings);
    var attestationAppend =
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
        sweptInterimResult, AttestationCommitProjection.fromVerifiedAppend(attestationAppend));
  }

  AttestedFiscalYearClose persistFiscalYearClose(
      SqliteNativeDatabase activeDatabase,
      SqliteAttestationEvidenceStore.ObservedHead observedHead,
      FiscalYearCloseDraft closeDraft,
      @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect derivedInterimSweep,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    AttestationOperationAuthorizer requiredAttestationAuthorizer =
        AttestationOperationAuthorizer.require(attestationAuthorizer);
    List<CommittedPosting> closePostings =
        generatedClosePostings.persistGeneratedPostings(
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
    var attestationAppend =
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
                derivedInterimSweep,
                closePostingSnapshots(closePostings)),
            requiredAttestationAuthorizer);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.fiscalYearClosed(
            closedFiscalYear.closedAt(), closedFiscalYear.closeOrder()));
    return new AttestedFiscalYearClose(
        closedFiscalYear, AttestationCommitProjection.fromVerifiedAppend(attestationAppend));
  }

  /**
   * Persists the unswept interim-result segment as an executor-derived fiscal-close effect.
   *
   * <p>The caller owns the one enclosing fiscal-year-close attestation append after this method
   * returns. This keeps an automatic sweep and its close inside one signed, atomic operation.
   */
  AttestationInterimResultSweepEffect persistInterimResultSweepAsFiscalCloseEffect(
      SqliteNativeDatabase activeDatabase,
      InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator) {
    List<CommittedPosting> closingPostings =
        generatedClosePostings.persistGeneratedPostings(
            activeDatabase,
            interimResultSweepDraft.closingPostings(),
            postingIdGenerator,
            FISCAL_YEAR_CLOSE_OPERATION.wireToken() + " interim-result sweep");
    RecordedInterimResultSweep sweptInterimResult =
        SqliteMutationWriter.insertInterimResultSweep(
            activeDatabase,
            interimResultSweepDraft.reportingPeriod(),
            interimResultSweepDraft.resultHoldingAccountCode(),
            interimResultSweepDraft.sweptTotals(),
            interimResultSweepDraft.sweptAt(),
            closingPostings);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.interimResultSwept(
            interimResultSweepDraft.sweptAt(), sweptInterimResult.sweepOrder()));
    return new AttestationInterimResultSweepEffect(
        interimResultSweepDraft.reportingPeriod(),
        interimResultSweepDraft.resultHoldingAccountCode().value(),
        sweptInterimResult.sweepOrder(),
        sweptInterimResult.sweptTotals(),
        closePostingSnapshots(closingPostings));
  }

  record AttestedInterimResultSweep(
      RecordedInterimResultSweep sweptInterimResult,
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
}
