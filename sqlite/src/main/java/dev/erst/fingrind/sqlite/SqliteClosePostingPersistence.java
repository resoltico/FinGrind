package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.RequestFingerprint;
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

/** Durable persistence for generated close postings inside SQLite close workflows. */
final class SqliteClosePostingPersistence {
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
      PostingDraft postingDraft,
      RequestFingerprint requestFingerprint,
      PostingIdGenerator postingIdGenerator) {
    CommittedPosting postingFact =
        postingDraft.materialize(
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator").nextPostingId());
    SqliteMutationWriter.insertPostingFact(
        activeDatabase,
        postingFact,
        Objects.requireNonNull(requestFingerprint, "requestFingerprint"));
    commitFaultHook.afterPostingFactInserted(postingFact);
    SqliteMutationWriter.insertJournalLines(activeDatabase, postingFact, commitFaultHook);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase, BookAuditEvent.postingCommitted(postingFact));
    return postingFact;
  }

  dev.erst.fingrind.executor.bookkeeping.SweptInterimResult persistInterimResultSweep(
      SqliteNativeDatabase activeDatabase,
      InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator) {
    SqliteTransactionValidationBook validationBook =
        new SqliteTransactionValidationBook(activeDatabase, context.postingReader(), true);
    PostingIdGenerator requiredPostingIdGenerator =
        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    List<CommittedPosting> closingPostings = new java.util.ArrayList<>();
    for (PostingDraft closingPostingDraft : interimResultSweepDraft.closingPostings()) {
      switch (postingAcceptancePolicy.decisionFor(closingPostingDraft, validationBook)) {
        case Decision.Replay replay -> closingPostings.add(replay.postingFact());
        case Decision.Rejected rejected ->
            throw new IllegalStateException(
                "Generated interim result sweep posting failed bookkeeping acceptance: "
                    + rejected.rejection());
        case Decision.Accepted accepted ->
            closingPostings.add(
                persistAcceptedPosting(
                    activeDatabase,
                    closingPostingDraft,
                    accepted.requestFingerprint(),
                    requiredPostingIdGenerator));
      }
    }
    SweptInterimResult sweptInterimResult =
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
    return sweptInterimResult;
  }

  ClosedFiscalYearRecord persistFiscalYearClose(
      SqliteNativeDatabase activeDatabase,
      FiscalYearCloseDraft closeDraft,
      PostingIdGenerator postingIdGenerator) {
    SqliteTransactionValidationBook validationBook =
        new SqliteTransactionValidationBook(activeDatabase, context.postingReader(), true);
    PostingIdGenerator requiredPostingIdGenerator =
        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    List<CommittedPosting> closePostings = new java.util.ArrayList<>();
    for (PostingDraft closePostingDraft : closeDraft.closePostingDrafts()) {
      switch (postingAcceptancePolicy.decisionFor(closePostingDraft, validationBook)) {
        case Decision.Replay replay -> closePostings.add(replay.postingFact());
        case Decision.Rejected rejected ->
            throw new IllegalStateException(
                "Generated fiscal year close posting failed bookkeeping acceptance: "
                    + rejected.rejection());
        case Decision.Accepted accepted ->
            closePostings.add(
                persistAcceptedPosting(
                    activeDatabase,
                    closePostingDraft,
                    accepted.requestFingerprint(),
                    requiredPostingIdGenerator));
      }
    }
    ClosedFiscalYearRecord closedFiscalYear =
        SqliteMutationWriter.insertFiscalYearClose(
            activeDatabase,
            closeDraft.reportingPeriod(),
            closeDraft.capitalAccountCode(),
            closeDraft.resultHoldingAccountCode(),
            closeDraft.retainedAccumulatedAccountCode(),
            closeDraft.closedAt(),
            closePostings);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.fiscalYearClosed(
            closedFiscalYear.closedAt(), closedFiscalYear.closeOrder()));
    return closedFiscalYear;
  }
}
