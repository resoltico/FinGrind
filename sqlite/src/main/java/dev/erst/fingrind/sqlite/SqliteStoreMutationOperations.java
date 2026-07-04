package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy.Decision;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Mutation operations over one SQLite-backed book session. */
final class SqliteStoreMutationOperations {
  /** One mutation callback that borrows the session-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext context;
  private final SqliteStoreLifecycle lifecycle;
  private final SqliteRekeyService rekeyService;
  private final PostingAcceptancePolicy postingAcceptancePolicy;
  private final SqliteStoreAdministrationMutationOperations administrationOperations;
  private final SqliteClosingMutationOperations closingOperations;

  SqliteStoreMutationOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this(context, lifecycle, SqliteCommitFaultHook.NONE, PostingAcceptancePolicy.currentKernel());
  }

  SqliteStoreMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook) {
    this(context, lifecycle, commitFaultHook, PostingAcceptancePolicy.currentKernel());
  }

  SqliteStoreMutationOperations(
      SqliteStoreContext context,
      SqliteStoreLifecycle lifecycle,
      SqliteCommitFaultHook commitFaultHook,
      PostingAcceptancePolicy postingAcceptancePolicy) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    this.rekeyService = new SqliteRekeyService(context, lifecycle);
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
    this.administrationOperations =
        new SqliteStoreAdministrationMutationOperations(context, lifecycle);
    this.closingOperations =
        new SqliteClosingMutationOperations(
            context,
            lifecycle,
            Objects.requireNonNull(commitFaultHook, "commitFaultHook"),
            this.postingAcceptancePolicy);
  }

  dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome openBook(
      Instant initializedAt,
      BookIdentity bookIdentity,
      List<dev.erst.fingrind.executor.bookkeeping.AccountDeclaration> seededAccounts) {
    return administrationOperations.openBook(initializedAt, bookIdentity, seededAccounts);
  }

  dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome declareAccount(
      dev.erst.fingrind.core.AccountCode accountCode,
      dev.erst.fingrind.core.AccountName accountName,
      dev.erst.fingrind.core.AccountType accountType,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return administrationOperations.declareAccount(
        accountCode, accountName, accountType, accountTaxonomy, declaredAt);
  }

  DeclareTaxRegistrationResult declareTaxRegistration(
      DeclareTaxRegistrationCommand command, Instant declaredAt) {
    return administrationOperations.declareTaxRegistration(command, declaredAt);
  }

  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    if (Files.notExists(context.bookPath())) {
      return new PostingCommitResult.Rejected(new BookkeepingPostingRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            transactionOwnership = lifecycle.transactions().beginImmediateIfNeeded(activeDatabase);
            Decision decision =
                postingAcceptancePolicy.decisionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            return switch (decision) {
              case Decision.Replay replay -> {
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Committed(replay.postingFact(), true);
              }
              case Decision.Rejected rejected -> {
                SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Rejected(rejected.rejection());
              }
              case Decision.Accepted accepted -> {
                CommittedPosting postingFact =
                    persistAcceptedPosting(
                        activeDatabase,
                        postingDraft,
                        accepted.requestFingerprint(),
                        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"));
                SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
                yield new PostingCommitResult.Committed(postingFact, false);
              }
            };
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to commit SQLite posting fact.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      InterimResultSweepPlanner planner,
      LocalDate currentUtcDate,
      Instant sweptAt,
      PostingIdGenerator postingIdGenerator) {
    return closingOperations.interimResultSweep(
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
    return closingOperations.interimResultSweep(
        throughEffectiveDate,
        bookStartDate,
        bookIdentity,
        planner,
        currentUtcDate,
        sweptAt,
        postingIdGenerator);
  }

  InterimResultSweepOutcome interimResultSweep(
      dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator) {
    return closingOperations.interimResultSweep(interimResultSweepDraft, postingIdGenerator);
  }

  FiscalYearCloseOutcome fiscalYearClose(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      FiscalYearClosePlanner planner,
      LocalDate currentUtcDate,
      Instant closedAt,
      PostingIdGenerator postingIdGenerator) {
    return closingOperations.fiscalYearClose(
        reportingPeriod, bookIdentity, planner, currentUtcDate, closedAt, postingIdGenerator);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    return rekeyService.rekeyBook(replacementPassphrase, rekeyedAt);
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }

  private CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      PostingDraft postingDraft,
      dev.erst.fingrind.core.RequestFingerprint requestFingerprint,
      PostingIdGenerator postingIdGenerator) {
    return closingOperations.persistAcceptedPosting(
        activeDatabase, postingDraft, requestFingerprint, postingIdGenerator);
  }
}
