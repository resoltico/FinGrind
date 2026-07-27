package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.core.attestation.AttestationInterimResultSweepEffect;
import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.AcceptedCloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.CloseTargetSelection;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseRequiresGeneratedPostings;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RejectedCloseTargetSelection;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Owns SQLite transaction flow for fiscal-year close mutations. */
final class SqliteFiscalYearCloseOperations {
  private final SqliteClosingMutationExecutionSupport executionSupport;
  private final SqliteClosingMutationReadSupport readSupport;
  private final SqliteClosePostingPersistence postingPersistence;

  SqliteFiscalYearCloseOperations(
      SqliteClosingMutationExecutionSupport executionSupport,
      SqliteClosingMutationReadSupport readSupport,
      SqliteClosePostingPersistence postingPersistence) {
    this.executionSupport = Objects.requireNonNull(executionSupport, "executionSupport");
    this.readSupport = Objects.requireNonNull(readSupport, "readSupport");
    this.postingPersistence = Objects.requireNonNull(postingPersistence, "postingPersistence");
  }

  FiscalYearCloseOutcome fiscalYearClose(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    executionSupport.requireWritableMutationSession();
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(closedAt, "closedAt");
    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    AttestationOperationAuthorizer.require(attestationAuthorizer);
    if (executionSupport.missingBookFile()) {
      return bookNotInitializedOutcome();
    }
    return executionSupport.withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          boolean committed = false;
          try {
            if (!executionSupport.isInitializedBook(activeDatabase)) {
              return bookNotInitializedOutcome();
            }
            SqliteAttestationEvidenceStore.ObservedHead observedHead =
                SqliteAttestationEvidenceStore.observeRequired(activeDatabase);
            transactionOwnership = executionSupport.beginImmediateIfNeeded(activeDatabase);
            Optional<BookkeepingAdministrationRejection> closeWindowRejection =
                planner.closeHorizonRejection(reportingPeriod, bookIdentity, currentUtcDate);
            if (closeWindowRejection.isPresent()) {
              return new FiscalYearCloseOutcome.Rejected(closeWindowRejection.orElseThrow());
            }
            Optional<ClosedFiscalYearRecord> existingClose =
                readSupport.loadFiscalYearClose(activeDatabase, reportingPeriod);
            if (existingClose.isPresent()) {
              SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
              committed = true;
              return new FiscalYearCloseOutcome.Closed(existingClose.orElseThrow(), true);
            }
            Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
                planner.closeHorizonRejection(
                    reportingPeriod,
                    bookIdentity,
                    currentUtcDate,
                    readSupport.loadTransferredThroughEffectiveDate(activeDatabase));
            if (closeHorizonRejection.isPresent()) {
              return new FiscalYearCloseOutcome.Rejected(closeHorizonRejection.orElseThrow());
            }
            List<RegisteredAccount> accounts = loadAllAccounts(activeDatabase);
            CloseTargetSelections closeTargets =
                selectCloseTargets(planner, bookIdentity, accounts);
            if (closeTargets.rejection() != null) {
              return new FiscalYearCloseOutcome.Rejected(closeTargets.rejection());
            }
            FiscalYearCloseDraft closeDraft =
                planner.closeDraft(
                    reportingPeriod,
                    bookIdentity,
                    closeTargets.requiredCapitalAccount(),
                    closeTargets.requiredResultHoldingAccount(),
                    closeTargets.requiredRetainedAccumulatedAccount(),
                    accounts,
                    readSupport.loadPostingsInRange(
                        activeDatabase, reportingPeriod.effectiveDateRange()),
                    readSupport.loadLatestTransferredThroughEffectiveDateWithinPeriod(
                        activeDatabase, reportingPeriod),
                    closedAt);
            if (closeDraft.closePostingDrafts().isEmpty()) {
              return new FiscalYearCloseOutcome.Rejected(
                  new FiscalYearCloseRequiresGeneratedPostings());
            }
            AttestationInterimResultSweepEffect derivedInterimSweep =
                persistGeneratedUnsweptInterimResultSweep(
                    activeDatabase, closeDraft, postingIdGenerator);
            var closedFiscalYear =
                postingPersistence.persistFiscalYearClose(
                    activeDatabase,
                    observedHead,
                    closeDraft,
                    derivedInterimSweep,
                    postingIdGenerator,
                    attestationAuthorizer);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            committed = true;
            return new FiscalYearCloseOutcome.Closed(
                closedFiscalYear.closedFiscalYear(), false, closedFiscalYear.attestationCommit());
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

  private static FiscalYearCloseOutcome.Rejected bookNotInitializedOutcome() {
    return new FiscalYearCloseOutcome.Rejected(
        new BookkeepingAdministrationRejection.BookNotInitialized());
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

  private static CloseTargetSelections selectCloseTargets(
      FiscalYearClosePlanner planner, BookIdentity bookIdentity, List<RegisteredAccount> accounts) {
    CloseTargetSelectionResult capitalSelection =
        closeTargetSelectionResult(planner.capitalAccount(accounts));
    if (capitalSelection.rejection() != null) {
      return new CloseTargetSelections(null, null, null, capitalSelection.rejection());
    }
    CloseTargetSelectionResult resultHoldingSelection =
        closeTargetSelectionResult(planner.resultHoldingAccount(bookIdentity, accounts));
    if (resultHoldingSelection.rejection() != null) {
      return new CloseTargetSelections(null, null, null, resultHoldingSelection.rejection());
    }
    CloseTargetSelectionResult retainedAccumulatedSelection =
        closeTargetSelectionResult(planner.retainedAccumulatedAccount(accounts));
    if (retainedAccumulatedSelection.rejection() != null) {
      return new CloseTargetSelections(null, null, null, retainedAccumulatedSelection.rejection());
    }
    return new CloseTargetSelections(
        capitalSelection.requiredAccount(),
        resultHoldingSelection.requiredAccount(),
        retainedAccumulatedSelection.requiredAccount(),
        null);
  }

  private List<RegisteredAccount> loadAllAccounts(SqliteNativeDatabase activeDatabase) {
    return SqliteAccountStatementQueries.loadAllAccounts(
        activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS);
  }

  private @org.jspecify.annotations.Nullable AttestationInterimResultSweepEffect
      persistGeneratedUnsweptInterimResultSweep(
          SqliteNativeDatabase activeDatabase,
          FiscalYearCloseDraft closeDraft,
          PostingIdGenerator postingIdGenerator) {
    if (closeDraft.unsweptInterimResultSweepDraft() == null) {
      return null;
    }
    return postingPersistence.persistInterimResultSweepAsFiscalCloseEffect(
        activeDatabase, closeDraft.unsweptInterimResultSweepDraft(), postingIdGenerator);
  }

  private record CloseTargetSelectionResult(
      @org.jspecify.annotations.Nullable RegisteredAccount account,
      @org.jspecify.annotations.Nullable BookkeepingAdministrationRejection rejection) {
    private RegisteredAccount requiredAccount() {
      return Objects.requireNonNull(account, "account");
    }
  }

  private record CloseTargetSelections(
      @org.jspecify.annotations.Nullable RegisteredAccount capitalAccount,
      @org.jspecify.annotations.Nullable RegisteredAccount resultHoldingAccount,
      @org.jspecify.annotations.Nullable RegisteredAccount retainedAccumulatedAccount,
      @org.jspecify.annotations.Nullable BookkeepingAdministrationRejection rejection) {
    private RegisteredAccount requiredCapitalAccount() {
      return Objects.requireNonNull(capitalAccount, "capitalAccount");
    }

    private RegisteredAccount requiredResultHoldingAccount() {
      return Objects.requireNonNull(resultHoldingAccount, "resultHoldingAccount");
    }

    private RegisteredAccount requiredRetainedAccumulatedAccount() {
      return Objects.requireNonNull(retainedAccumulatedAccount, "retainedAccumulatedAccount");
    }
  }
}
