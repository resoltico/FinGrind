package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.executor.bookkeeping.AcceptedPosting;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Close-operation persistence and SQLite transaction coordination for reporting-period workflows.
 */
final class SqliteClosingMutationOperations {
  private final SqliteClosePostingPersistence postingPersistence;
  private final SqliteInterimResultSweepOperations interimResultSweepOperations;
  private final SqliteFiscalYearCloseOperations fiscalYearCloseOperations;

  SqliteClosingMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(lifecycle, "lifecycle");
    this.postingPersistence =
        new SqliteClosePostingPersistence(context, commitFaultHook, postingAcceptancePolicy);
    SqliteClosingMutationExecutionSupport executionSupport =
        new SqliteClosingMutationExecutionSupport(context, lifecycle);
    SqliteClosingMutationReadSupport readSupport = new SqliteClosingMutationReadSupport(context);
    this.interimResultSweepOperations =
        new SqliteInterimResultSweepOperations(executionSupport, readSupport, postingPersistence);
    this.fiscalYearCloseOperations =
        new SqliteFiscalYearCloseOperations(executionSupport, readSupport, postingPersistence);
  }

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    return interimResultSweepOperations.interimResultSweep(
        reportingPeriod, bookIdentity, planner, currentUtcDate, sweptAt, postingIdGenerator);
  }

  InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    return interimResultSweepOperations.interimResultSweep(
        throughEffectiveDate,
        bookStartDate,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator);
  }

  InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft, PostingIdGenerator postingIdGenerator) {
    return interimResultSweepOperations.interimResultSweep(
        interimResultSweepDraft, postingIdGenerator);
  }

  FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator) {
    return fiscalYearCloseOperations.fiscalYearClose(
        reportingPeriod, bookIdentity, planner, currentUtcDate, closedAt, postingIdGenerator);
  }

  CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      AcceptedPosting acceptedPosting,
      dev.erst.fingrind.core.RequestFingerprint requestFingerprint,
      CommittedProvenance provenance,
      PostingIdGenerator postingIdGenerator) {
    return postingPersistence.persistAcceptedPosting(
        activeDatabase, acceptedPosting, requestFingerprint, provenance, postingIdGenerator);
  }

  void persistMaterializedPosting(
      SqliteNativeDatabase activeDatabase,
      AcceptedPosting acceptedPosting,
      dev.erst.fingrind.core.RequestFingerprint requestFingerprint,
      CommittedPosting postingFact) {
    postingPersistence.persistMaterializedPosting(
        activeDatabase, acceptedPosting, postingFact, requestFingerprint);
  }
}
