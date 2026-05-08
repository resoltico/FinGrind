package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingDraft;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

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

  SqliteStoreMutationOperations(SqliteStoreContext context, SqliteStoreLifecycle lifecycle) {
    this.context = Objects.requireNonNull(context, "context");
    this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
  }

  BookOpeningOutcome openBook(Instant initializedAt) {
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
            SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            lifecycle.cacheState(
                new SqliteBookStateSnapshot(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookState.INITIALIZED_FINGRIND));
            return new BookOpeningOutcome.Opened(initializedAt);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to initialize SQLite book.", exception);
          }
        });
  }

  AccountDeclarationOutcome declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
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
                    new AccountDeclaration(accountCode, accountName, normalBalance),
                    declaredAt);
            if (declarationOutcome instanceof AccountDeclarationOutcome.Rejected rejected) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return rejected;
            }
            RegisteredAccount declaredAccount =
                ((AccountDeclarationOutcome.Declared) declarationOutcome).account();
            SqliteMutationWriter.upsertAccount(activeDatabase, declaredAccount);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new AccountDeclarationOutcome.Declared(declaredAccount);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to declare SQLite book account.", exception);
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
                PostingAcceptancePolicy.rejectionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, context.postingReader()));
            if (ordinaryOutcome.isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PostingCommitResult.Rejected(ordinaryOutcome.orElseThrow());
            }
            CommittedPosting postingFact =
                postingDraft.materialize(
                    Objects.requireNonNull(postingIdGenerator, "postingIdGenerator")
                        .nextPostingId());
            SqliteMutationWriter.insertPostingFact(activeDatabase, postingFact);
            SqliteMutationWriter.insertJournalLines(activeDatabase, postingFact);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new PostingCommitResult.Committed(postingFact);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to commit SQLite posting fact.", exception);
          }
        });
  }

  RekeyBookResult rekeyBook(SqliteBookPassphrase replacementPassphrase) {
    lifecycle.ensureOpenSession();
    context.accessMode().requireWritableMutation();
    try (SqliteOwnedPassphrase activeReplacementPassphrase =
        new SqliteOwnedPassphrase(
            Objects.requireNonNull(replacementPassphrase, "replacementPassphrase"))) {
      if (Files.notExists(context.bookPath())) {
        return new RekeyBookResult.Rejected(
            new dev.erst.fingrind.contract.BookAdministrationRejection.BookNotInitialized());
      }
      return withBorrowedDatabase(
          activeDatabase -> {
            if (!lifecycle.isInitializedBook(activeDatabase)) {
              return new RekeyBookResult.Rejected(
                  new dev.erst.fingrind.contract.BookAdministrationRejection.BookNotInitialized());
            }
            SqliteRekeyRollbackFile rollbackFile =
                SqliteRekeyRollbackFile.create(context.bookPath());
            SqliteNativeConnections.rekey(
                activeDatabase, activeReplacementPassphrase.nativePassphrase());
            try {
              return publishRekeyedDatabase(
                  activeDatabase, activeReplacementPassphrase.nativePassphrase(), rollbackFile);
            } catch (RuntimeException exception) {
              RuntimeException closeFailure =
                  captureBestEffortRuntimeFailure(
                      () -> SqliteStoreContext.closeOwnedDatabase(activeDatabase));
              lifecycle.clearDatabaseState();
              RuntimeException restoreFailure =
                  captureBestEffortRuntimeFailure(() -> rollbackFile.restore(context.bookPath()));
              throw finalizeFailedRekey(
                  exception, restoreFailure, closeFailure, rollbackFile::deleteQuietly);
            }
          });
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to rekey SQLite book.", exception);
    }
  }

  RekeyBookResult publishRekeyedDatabase(
      SqliteNativeDatabase activeDatabase,
      SqliteBookPassphrase replacementPassphrase,
      SqliteRekeyRollbackFile rollbackFile) {
    SqliteNativeDatabase reopenedDatabase = context.openConfiguredDatabase(replacementPassphrase);
    boolean published = false;
    try {
      lifecycle.requireInitializedBook(reopenedDatabase);
      SqliteStoreContext.closeOwnedDatabase(activeDatabase);
      lifecycle.clearDatabaseState();
      lifecycle.publishDatabase(reopenedDatabase);
      lifecycle.rotateSessionSecret(replacementPassphrase);
      published = true;
      rollbackFile.deleteQuietly();
      return new RekeyBookResult.Rekeyed(context.bookPath());
    } finally {
      if (!published) {
        SqliteStoreOperations.closeReopenedDatabaseQuietly(reopenedDatabase);
      }
    }
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(lifecycle.database());
  }

  static @Nullable RuntimeException captureBestEffortRuntimeFailure(
      BestEffortRuntimeAction action) {
    try {
      action.run();
      return null;
    } catch (RuntimeException failure) {
      return failure;
    }
  }

  static IllegalStateException catastrophicRekeyRestoreFailure(
      RuntimeException verificationFailure,
      RuntimeException restoreFailure,
      @Nullable RuntimeException closeFailure) {
    IllegalStateException catastrophicFailure =
        new IllegalStateException(
            "Failed to verify the rekeyed SQLite book, and FinGrind could not restore the pre-rekey book automatically. Use the preserved rollback copy in the reported storage failure to recover manually.",
            verificationFailure);
    catastrophicFailure.addSuppressed(restoreFailure);
    if (closeFailure != null) {
      catastrophicFailure.addSuppressed(closeFailure);
    }
    return catastrophicFailure;
  }

  static IllegalStateException finalizeFailedRekey(
      RuntimeException verificationFailure,
      @Nullable RuntimeException restoreFailure,
      @Nullable RuntimeException closeFailure,
      BestEffortRuntimeAction rollbackDeleteAction) {
    Objects.requireNonNull(rollbackDeleteAction, "rollbackDeleteAction");
    if (restoreFailure != null) {
      return catastrophicRekeyRestoreFailure(verificationFailure, restoreFailure, closeFailure);
    }
    rollbackDeleteAction.run();
    return restoredOriginalBookFailure(verificationFailure, closeFailure);
  }

  static IllegalStateException restoredOriginalBookFailure(
      RuntimeException verificationFailure, @Nullable RuntimeException closeFailure) {
    Objects.requireNonNull(verificationFailure, "verificationFailure");
    if (closeFailure != null) {
      verificationFailure.addSuppressed(closeFailure);
    }
    return new IllegalStateException(
        "Failed to verify the rekeyed SQLite book. FinGrind restored the pre-rekey book on disk; reopen the session with the original passphrase and retry.",
        verificationFailure);
  }

  /** One action that may raise a runtime failure while preserving the primary rekey outcome. */
  @FunctionalInterface
  interface BestEffortRuntimeAction {
    /** Runs one best-effort runtime action. */
    void run();
  }
}
