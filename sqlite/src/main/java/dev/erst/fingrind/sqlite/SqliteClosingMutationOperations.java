package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.AcceptedInterimResultTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlan;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.RejectedInterimResultTargetSelection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Close-operation persistence and SQLite transaction coordination for reporting-period workflows.
 */
final class SqliteClosingMutationOperations {
  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteClosingMutationReadSupport readSupport;
  private final SqliteClosePostingPersistence postingPersistence;

  SqliteClosingMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.readSupport = new SqliteClosingMutationReadSupport(context);
    this.postingPersistence =
        new SqliteClosePostingPersistence(context, commitFaultHook, postingAcceptancePolicy);
  }

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(sweptAt, "sweptAt");
    if (Files.notExists(context.bookPath())) {
      return new InterimResultSweepOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new InterimResultSweepOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            List<RegisteredAccount> accounts =
                SqliteAccountStatementQueries.loadAllAccounts(
                    activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS);
            var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
            if (resultHoldingSelection instanceof RejectedInterimResultTargetSelection rejected) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new InterimResultSweepOutcome.Rejected(rejected.rejection());
            }
            Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
                planner.closeHorizonRejection(
                    reportingPeriod,
                    bookIdentity,
                    currentUtcDate,
                    readSupport.loadTransferredThroughEffectiveDate(activeDatabase));
            if (closeHorizonRejection.isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new InterimResultSweepOutcome.Rejected(closeHorizonRejection.orElseThrow());
            }
            RegisteredAccount resultHoldingAccount =
                ((AcceptedInterimResultTargetSelection) resultHoldingSelection).account();
            InterimResultSweepPlan closePlan =
                planner.closingPostings(
                    reportingPeriod,
                    resultHoldingAccount,
                    accounts,
                    readSupport.loadPostingsInRange(
                        activeDatabase, reportingPeriod.effectiveDateRange()),
                    sweptAt);
            var sweptInterimResult =
                postingPersistence.persistInterimResultSweep(
                    activeDatabase,
                    new InterimResultSweepDraft(
                        reportingPeriod,
                        resultHoldingAccount.accountCode(),
                        closePlan.sweptTotals(),
                        sweptAt,
                        closePlan.closingPostings()),
                    postingIdGenerator);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new InterimResultSweepOutcome.Transferred(sweptInterimResult);
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

  InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft, PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    if (Files.notExists(context.bookPath())) {
      return new InterimResultSweepOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          boolean committed = false;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new InterimResultSweepOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            var sweptInterimResult =
                postingPersistence.persistInterimResultSweep(
                    activeDatabase, interimResultSweepDraft, postingIdGenerator);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            committed = true;
            return new InterimResultSweepOutcome.Transferred(sweptInterimResult);
          } catch (SqliteNativeException exception) {
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to close one SQLite reporting period.", exception);
          } finally {
            if (!committed) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            }
          }
        });
  }

  FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(closedAt, "closedAt");
    if (Files.notExists(context.bookPath())) {
      return new FiscalYearCloseOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          boolean committed = false;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new FiscalYearCloseOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            List<RegisteredAccount> accounts =
                SqliteAccountStatementQueries.loadAllAccounts(
                    activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS);
            CloseTargetSelectionResult capitalSelection =
                closeTargetSelectionResult(planner.capitalAccount(accounts));
            if (capitalSelection.rejection() != null) {
              return new FiscalYearCloseOutcome.Rejected(capitalSelection.rejection());
            }
            CloseTargetSelectionResult resultHoldingSelection =
                closeTargetSelectionResult(planner.resultHoldingAccount(bookIdentity, accounts));
            if (resultHoldingSelection.rejection() != null) {
              return new FiscalYearCloseOutcome.Rejected(resultHoldingSelection.rejection());
            }
            CloseTargetSelectionResult retainedAccumulatedSelection =
                closeTargetSelectionResult(planner.retainedAccumulatedAccount(accounts));
            if (retainedAccumulatedSelection.rejection() != null) {
              return new FiscalYearCloseOutcome.Rejected(retainedAccumulatedSelection.rejection());
            }
            Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
                planner.closeHorizonRejection(reportingPeriod, bookIdentity, currentUtcDate);
            if (closeHorizonRejection.isPresent()) {
              return new FiscalYearCloseOutcome.Rejected(closeHorizonRejection.orElseThrow());
            }
            FiscalYearCloseDraft closeDraft =
                planner.closeDraft(
                    reportingPeriod,
                    bookIdentity,
                    capitalSelection.requiredAccount(),
                    resultHoldingSelection.requiredAccount(),
                    retainedAccumulatedSelection.requiredAccount(),
                    accounts,
                    readSupport.loadPostingsInRange(
                        activeDatabase, reportingPeriod.effectiveDateRange()),
                    readSupport.loadLatestTransferredThroughEffectiveDateWithinPeriod(
                        activeDatabase, reportingPeriod),
                    closedAt);
            persistGeneratedUnsweptInterimResultSweep(
                activeDatabase, closeDraft, postingIdGenerator);
            ClosedFiscalYearRecord closedFiscalYear =
                postingPersistence.persistFiscalYearClose(
                    activeDatabase,
                    closeDraft,
                    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            committed = true;
            return new FiscalYearCloseOutcome.Closed(closedFiscalYear);
          } catch (SqliteNativeException exception) {
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to close one SQLite fiscal year.", exception);
          } finally {
            if (!committed) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            }
          }
        });
  }

  CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      dev.erst.fingrind.executor.spi.PostingDraft postingDraft,
      dev.erst.fingrind.core.RequestFingerprint requestFingerprint,
      PostingIdGenerator postingIdGenerator) {
    return postingPersistence.persistAcceptedPosting(
        activeDatabase, postingDraft, requestFingerprint, postingIdGenerator);
  }

  private static CloseTargetSelectionResult closeTargetSelectionResult(
      CloseTargetSelection selection) {
    return switch (selection) {
      case AcceptedCloseTargetSelection accepted ->
          new CloseTargetSelectionResult(accepted.account(), null);
      case RejectedCloseTargetSelection rejected ->
          new CloseTargetSelectionResult(null, rejected.rejection());
    };
  }

  private void persistGeneratedUnsweptInterimResultSweep(
      SqliteNativeDatabase activeDatabase,
      FiscalYearCloseDraft closeDraft,
      PostingIdGenerator postingIdGenerator) {
    if (closeDraft.unsweptInterimResultSweepDraft() == null) {
      return;
    }
    postingPersistence.persistInterimResultSweep(
        activeDatabase, closeDraft.unsweptInterimResultSweepDraft(), postingIdGenerator);
  }

  private record CloseTargetSelectionResult(
      @org.jspecify.annotations.Nullable RegisteredAccount account,
      @org.jspecify.annotations.Nullable BookkeepingAdministrationRejection rejection) {
    private RegisteredAccount requiredAccount() {
      return Objects.requireNonNull(account, "account");
    }
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }
}
