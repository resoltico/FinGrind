package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AcceptedInterimResultTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlan;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RejectedInterimResultTargetSelection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns SQLite transaction flow for interim-result sweep mutations. */
final class SqliteInterimResultSweepOperations {
  /** Resolves one sweep window from the active database state before persistence begins. */
  @FunctionalInterface
  private interface SweepWindowResolver {
    /** Resolves one persisted reporting window or one deterministic close-horizon rejection. */
    SweepPlanningResult resolve(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteClosingMutationExecutionSupport executionSupport;
  private final SqliteClosingMutationReadSupport readSupport;
  private final SqliteClosePostingPersistence postingPersistence;

  SqliteInterimResultSweepOperations(
      SqliteClosingMutationExecutionSupport executionSupport,
      SqliteClosingMutationReadSupport readSupport,
      SqliteClosePostingPersistence postingPersistence) {
    this.executionSupport = Objects.requireNonNull(executionSupport, "executionSupport");
    this.readSupport = Objects.requireNonNull(readSupport, "readSupport");
    this.postingPersistence = Objects.requireNonNull(postingPersistence, "postingPersistence");
  }

  InterimResultSweepOutcome interimResultSweep(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    return plannedInterimResultSweep(
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer,
        activeDatabase ->
            new SweepPlanningResult(
                reportingPeriod,
                planner
                    .closeHorizonRejection(
                        reportingPeriod,
                        bookIdentity,
                        currentUtcDate,
                        readSupport.loadTransferredThroughEffectiveDate(activeDatabase))
                    .orElse(null)));
  }

  InterimResultSweepOutcome interimResultSweep(
      LocalDate throughEffectiveDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    return plannedInterimResultSweep(
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator,
        attestationAuthorizer,
        activeDatabase ->
            derivedSweepPlanning(
                activeDatabase, throughEffectiveDate, bookIdentity, planner, currentUtcDate));
  }

  private InterimResultSweepOutcome plannedInterimResultSweep(
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer,
      SweepWindowResolver sweepWindowResolver) {
    executionSupport.requireWritableMutationSession();
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(sweptAt, "sweptAt");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    Objects.requireNonNull(sweepWindowResolver, "sweepWindowResolver");
    if (executionSupport.missingBookFile()) {
      return bookNotInitializedOutcome();
    }
    return executionSupport.withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!executionSupport.isInitializedBook(activeDatabase)) {
              return bookNotInitializedOutcome();
            }
            SqliteAttestationEvidenceStore.ObservedHead observedHead =
                SqliteAttestationEvidenceStore.observeRequired(activeDatabase);
            transactionOwnership = executionSupport.beginImmediateIfNeeded(activeDatabase);
            List<RegisteredAccount> accounts = loadAllAccounts(activeDatabase);
            var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
            if (resultHoldingSelection instanceof RejectedInterimResultTargetSelection rejected) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new InterimResultSweepOutcome.Rejected(rejected.rejection());
            }
            SweepPlanningResult sweepPlanning = sweepWindowResolver.resolve(activeDatabase);
            if (sweepPlanning.rejection() != null) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new InterimResultSweepOutcome.Rejected(sweepPlanning.rejection());
            }
            RegisteredAccount resultHoldingAccount =
                ((AcceptedInterimResultTargetSelection) resultHoldingSelection).account();
            InterimResultSweepPlan closePlan =
                planner.closingPostings(
                    sweepPlanning.requiredReportingPeriod(),
                    resultHoldingAccount,
                    accounts,
                    readSupport.loadPostingsInRange(
                        activeDatabase,
                        sweepPlanning.requiredReportingPeriod().effectiveDateRange()),
                    sweptAt);
            var sweptInterimResult =
                postingPersistence.persistInterimResultSweep(
                    activeDatabase,
                    observedHead,
                    new InterimResultSweepDraft(
                        sweepPlanning.requiredReportingPeriod(),
                        resultHoldingAccount.accountCode(),
                        closePlan.sweptTotals(),
                        sweptAt,
                        closePlan.closingPostings()),
                    postingIdGenerator,
                    attestationAuthorizer);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new InterimResultSweepOutcome.Transferred(
                sweptInterimResult.sweptInterimResult(), sweptInterimResult.attestationCommit());
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to close one SQLite reporting period.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  private SweepPlanningResult derivedSweepPlanning(
      SqliteNativeDatabase activeDatabase,
      LocalDate throughEffectiveDate,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate) {
    Optional<LocalDate> transferredThroughEffectiveDate =
        readSupport.loadTransferredThroughEffectiveDate(activeDatabase);
    Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
        planner.closeHorizonRejection(
            throughEffectiveDate, bookIdentity, currentUtcDate, transferredThroughEffectiveDate);
    if (closeHorizonRejection.isPresent()) {
      return new SweepPlanningResult(null, closeHorizonRejection.orElseThrow());
    }
    return new SweepPlanningResult(
        planner.reportingPeriod(
            throughEffectiveDate, bookIdentity, transferredThroughEffectiveDate),
        null);
  }

  private List<RegisteredAccount> loadAllAccounts(SqliteNativeDatabase activeDatabase) {
    return SqliteAccountStatementQueries.loadAllAccounts(
        activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS);
  }

  private static InterimResultSweepOutcome.Rejected bookNotInitializedOutcome() {
    return new InterimResultSweepOutcome.Rejected(
        new BookkeepingAdministrationRejection.BookNotInitialized());
  }

  private record SweepPlanningResult(
      @Nullable ReportingPeriod reportingPeriod,
      @Nullable BookkeepingAdministrationRejection rejection) {
    private ReportingPeriod requiredReportingPeriod() {
      return Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    }
  }
}
