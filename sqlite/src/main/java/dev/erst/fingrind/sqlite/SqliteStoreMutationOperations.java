package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountTaxonomy;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookAuditEvent;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TransferredPeriodResult;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.nio.file.Files;
import java.time.Instant;
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
  }

  BookOpeningOutcome openBook(Instant initializedAt, BookIdentity bookIdentity) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableInitialization();
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteBookStateSnapshot snapshot = lifecycle.stateSnapshot(activeDatabase);
            Optional<BookOpeningOutcome> preexistingOutcome =
                snapshot.state().openBookResult(snapshot.userVersion());
            if (preexistingOutcome.isPresent()) {
              return preexistingOutcome.orElseThrow();
            }

            transactionOwnership = lifecycle.beginImmediateIfNeeded(activeDatabase);
            SqliteBookSchemaBootstrap.initializeBook(activeDatabase);
            SqliteBookIntegrityVerifier.recordSchemaFingerprint(activeDatabase);
            SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
            SqliteMutationWriter.insertBookIdentity(activeDatabase, bookIdentity);
            SqliteAuditEventWriter.insertAuditEvent(
                activeDatabase, BookAuditEvent.bookOpened(initializedAt));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            lifecycle.cacheState(
                new SqliteBookStateSnapshot(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookState.INITIALIZED_FINGRIND));
            return new BookOpeningOutcome.Opened(initializedAt, bookIdentity);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to initialize SQLite book.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
  }

  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      AccountType accountType,
      AccountRole accountRole,
      AccountTaxonomy accountTaxonomy,
      Instant declaredAt) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    if (Files.notExists(context.bookPath())) {
      return new AccountDeclarationOutcome.Rejected(
          new BookkeepingAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new AccountDeclarationOutcome.Rejected(
                  new BookkeepingAdministrationRejection.BookNotInitialized());
            }

            transactionOwnership = lifecycle.beginImmediateIfNeeded(activeDatabase);
            Optional<RegisteredAccount> existingAccount =
                SqliteStatementQueries.findOneAccount(activeDatabase, accountCode);
            AccountDeclarationOutcome declarationOutcome =
                RegisteredAccount.declare(
                    existingAccount.orElse(null),
                    new AccountDeclaration(
                        accountCode, accountName, accountType, accountRole, accountTaxonomy),
                    declaredAt);
            if (declarationOutcome instanceof AccountDeclarationOutcome.Rejected rejected) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return rejected;
            }
            RegisteredAccount declaredAccount =
                ((AccountDeclarationOutcome.Declared) declarationOutcome).account();
            SqliteMutationWriter.upsertAccount(activeDatabase, declaredAccount);
            SqliteAuditEventWriter.insertAuditEvent(
                activeDatabase,
                BookAuditEvent.accountDeclared(
                    declaredAt,
                    declaredAccount.accountCode(),
                    existingAccount.isPresent() && !existingAccount.orElseThrow().active()));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new AccountDeclarationOutcome.Declared(declaredAccount);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to declare SQLite book account.", exception);
          } catch (RuntimeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw exception;
          }
        });
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
                  persistAcceptedPosting(
                      activeDatabase, closingPostingDraft, requiredPostingIdGenerator));
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
                    periodResultTransferDraft.transferredAt(),
                    transferredPeriodResult.transferOrder()));
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            committed = true;
            return new PeriodResultTransferOutcome.Transferred(transferredPeriodResult);
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

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase, Instant rekeyedAt) {
    return rekeyService.rekeyBook(replacementPassphrase, rekeyedAt);
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
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
