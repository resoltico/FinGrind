package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;

/** Mutation surface over one SQLite posting-fact store. */
interface SqlitePostingFactStoreMutationView extends SqliteAttestedAdministrationMutationView {

  /** Commits one posting draft into the protected book. */
  default PostingCommitResult commit(
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .commit(postingDraft, postingIdGenerator, attestationAuthorizer);
  }

  /** Commits a generated interim-result sweep into the protected book. */
  default InterimResultSweepOutcome interimResultSweep(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            sweptAt,
            postingIdGenerator,
            attestationAuthorizer);
  }

  /** Commits a preplanned interim-result sweep into the protected book. */
  default InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .interimResultSweep(interimResultSweepDraft, postingIdGenerator, attestationAuthorizer);
  }

  /** Commits one fiscal-year close into the protected book. */
  default FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    storeThreadOwner().requireOwnerThread();
    return storeMutationOperations()
        .fiscalYearClose(
            reportingPeriod,
            bookIdentity,
            planner,
            currentUtcDate,
            closedAt,
            postingIdGenerator,
            attestationAuthorizer);
  }
}
