package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
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
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return interimResultSweepOperations.interimResultSweep(
        reportingPeriod,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer);
  }

  InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return interimResultSweepOperations.interimResultSweep(
        throughEffectiveDate,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer);
  }

  FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return fiscalYearCloseOperations.fiscalYearClose(
        reportingPeriod,
        bookIdentity,
        planner,
        currentUtcDate,
        closedAt,
        postingIdGenerator,
        attestationAuthorizer);
  }
}
