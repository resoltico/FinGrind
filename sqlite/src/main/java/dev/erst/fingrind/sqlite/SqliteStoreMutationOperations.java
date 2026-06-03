package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.executor.bookkeeping.AcceptedResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlan;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferPlanner;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.RejectedResultHoldingSelection;
import dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
  private final SqliteCommitFaultHook commitFaultHook;
  private final SqliteRekeyService rekeyService;
  private final PostingAcceptancePolicy postingAcceptancePolicy;
  private final SqliteStoreAdministrationMutationOperations administrationOperations;

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
    this.commitFaultHook = Objects.requireNonNull(commitFaultHook, "commitFaultHook");
    this.rekeyService = new SqliteRekeyService(context, lifecycle);
    this.postingAcceptancePolicy =
        Objects.requireNonNull(postingAcceptancePolicy, "postingAcceptancePolicy");
    this.administrationOperations =
        new SqliteStoreAdministrationMutationOperations(context, lifecycle);
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
      dev.erst.fingrind.core.AccountRole accountRole,
      dev.erst.fingrind.core.AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    return administrationOperations.declareAccount(
        accountCode, accountName, accountType, accountRole, accountTaxonomy, declaredAt);
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
            transactionOwnership = lifecycle.beginImmediateIfNeeded(activeDatabase);
            Optional<BookkeepingPostingRejection> ordinaryOutcome =
                postingAcceptancePolicy.rejectionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            if (ordinaryOutcome.isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PostingCommitResult.Rejected(ordinaryOutcome.orElseThrow());
            }
            CommittedPosting postingFact =
                persistAcceptedPosting(
                    activeDatabase,
                    postingDraft,
                    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator"));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new PostingCommitResult.Committed(postingFact);
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

  PeriodResultTransferOutcome transferPeriodResult(
      dev.erst.fingrind.core.ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      PeriodResultTransferPlanner planner,
      LocalDate currentUtcDate,
      Instant transferredAt,
      PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(planner, "planner");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(transferredAt, "transferredAt");
    if (Files.notExists(context.bookPath())) {
      return new PeriodResultTransferOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new PeriodResultTransferOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            transactionOwnership = lifecycle.beginImmediateIfNeeded(activeDatabase);
            List<RegisteredAccount> accounts =
                SqliteStatementQueries.loadAllAccounts(
                    activeDatabase, SqlitePostingSql.LOAD_ALL_ACCOUNTS);
            var resultHoldingSelection = planner.resultHoldingAccount(bookIdentity, accounts);
            if (resultHoldingSelection instanceof RejectedResultHoldingSelection rejected) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PeriodResultTransferOutcome.Rejected(rejected.rejection());
            }
            Optional<BookkeepingAdministrationRejection> closeHorizonRejection =
                planner.closeHorizonRejection(
                    reportingPeriod,
                    bookIdentity,
                    currentUtcDate,
                    loadTransferredThroughEffectiveDate(activeDatabase));
            if (closeHorizonRejection.isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PeriodResultTransferOutcome.Rejected(closeHorizonRejection.orElseThrow());
            }
            RegisteredAccount resultHoldingAccount =
                ((AcceptedResultHoldingSelection) resultHoldingSelection).account();
            PeriodResultTransferPlan closePlan =
                planner.closingPostings(
                    reportingPeriod,
                    resultHoldingAccount,
                    accounts,
                    loadPostingsInRange(activeDatabase, reportingPeriod.effectiveDateRange()),
                    transferredAt);
            PeriodResultTransferOutcome outcome =
                persistPeriodResultTransfer(
                    activeDatabase,
                    new PeriodResultTransferDraft(
                        reportingPeriod,
                        resultHoldingAccount.accountCode(),
                        closePlan.transferredTotals(),
                        transferredAt,
                        closePlan.closingPostings()),
                    postingIdGenerator);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return outcome;
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

  PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    if (Files.notExists(context.bookPath())) {
      return new PeriodResultTransferOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          boolean committed = false;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new PeriodResultTransferOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }
            transactionOwnership = lifecycle.beginImmediateIfNeeded(activeDatabase);
            PeriodResultTransferOutcome outcome =
                persistPeriodResultTransfer(
                    activeDatabase, periodResultTransferDraft, postingIdGenerator);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            committed = true;
            return outcome;
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

  private List<CommittedPosting> loadPostingsInRange(
      SqliteNativeDatabase activeDatabase, dev.erst.fingrind.core.EffectiveDateRange range) {
    return context
        .postingReader()
        .loadCommittedPostings(
            activeDatabase,
            SqlitePostingSql.LOAD_POSTINGS_IN_RANGE,
            statement -> {
              String effectiveDateFrom =
                  range
                      .effectiveDateFrom()
                      .map(CanonicalTemporalText::formatLocalDate)
                      .orElse(null);
              String effectiveDateTo =
                  range.effectiveDateTo().map(CanonicalTemporalText::formatLocalDate).orElse(null);
              statement.bindText(1, effectiveDateFrom);
              statement.bindText(2, effectiveDateFrom);
              statement.bindText(3, effectiveDateTo);
              statement.bindText(4, effectiveDateTo);
            });
  }

  private Optional<LocalDate> loadTransferredThroughEffectiveDate(
      SqliteNativeDatabase activeDatabase) {
    return SqliteStatementQueries.loadOptionalText(
            activeDatabase, SqlitePostingSql.FIND_CLOSED_THROUGH_EFFECTIVE_DATE, statement -> {})
        .map(LocalDate::parse);
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    return rekeyService.rekeyBook(replacementPassphrase, rekeyedAt);
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }

  private PeriodResultTransferOutcome persistPeriodResultTransfer(
      SqliteNativeDatabase activeDatabase,
      PeriodResultTransferDraft periodResultTransferDraft,
      PostingIdGenerator postingIdGenerator) {
    SqliteTransactionValidationBook validationBook =
        new SqliteTransactionValidationBook(activeDatabase, context.postingReader());
    PostingIdGenerator requiredPostingIdGenerator =
        Objects.requireNonNull(postingIdGenerator, "postingIdGenerator");
    List<CommittedPosting> closingPostings = new java.util.ArrayList<>();
    for (PostingDraft closingPostingDraft : periodResultTransferDraft.closingPostings()) {
      Optional<BookkeepingPostingRejection> rejection =
          postingAcceptancePolicy.rejectionFor(closingPostingDraft, validationBook);
      if (rejection.isPresent()) {
        throw new IllegalStateException(
            "Generated period-result-transfer posting failed bookkeeping acceptance: "
                + rejection.orElseThrow());
      }
      closingPostings.add(
          persistAcceptedPosting(activeDatabase, closingPostingDraft, requiredPostingIdGenerator));
    }
    TransferredPeriodResult transferredPeriodResult =
        SqliteMutationWriter.insertPeriodResultTransfer(
            activeDatabase,
            periodResultTransferDraft.reportingPeriod(),
            periodResultTransferDraft.resultHoldingAccountCode(),
            periodResultTransferDraft.transferredTotals(),
            periodResultTransferDraft.transferredAt(),
            closingPostings);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase,
        BookAuditEvent.periodResultTransferred(
            periodResultTransferDraft.transferredAt(), transferredPeriodResult.transferOrder()));
    return new PeriodResultTransferOutcome.Transferred(transferredPeriodResult);
  }

  private CommittedPosting persistAcceptedPosting(
      SqliteNativeDatabase activeDatabase,
      PostingDraft postingDraft,
      PostingIdGenerator postingIdGenerator) {
    CommittedPosting postingFact =
        postingDraft.materialize(
            Objects.requireNonNull(postingIdGenerator, "postingIdGenerator").nextPostingId());
    SqliteMutationWriter.insertPostingFact(activeDatabase, postingFact);
    commitFaultHook.afterPostingFactInserted(postingFact);
    SqliteMutationWriter.insertJournalLines(activeDatabase, postingFact, commitFaultHook);
    SqliteAuditEventWriter.insertAuditEvent(
        activeDatabase, BookAuditEvent.postingCommitted(postingFact));
    return postingFact;
  }
}
