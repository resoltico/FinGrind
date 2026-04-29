package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingIdGenerator;
import dev.erst.fingrind.executor.PostingValidation;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Mutation operations over one SQLite-backed book session. */
final class SqliteStoreMutationOperations {
  /** One mutation callback that borrows the store-owned SQLite handle without closing it. */
  @FunctionalInterface
  private interface BorrowedDatabaseAction<T> {
    /** Runs one mutation callback against the active store-owned SQLite handle. */
    T run(SqliteNativeDatabase activeDatabase);
  }

  private final SqliteStoreContext store;

  SqliteStoreMutationOperations(SqliteStoreContext store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  OpenBookResult openBook(Instant initializedAt) {
    store.ensureOpenSession();
    store.accessMode().requireWritableInitialization();
    SqliteBookSchemaBootstrap.ensureParentDirectory(store.bookPath());
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            SqliteBookStateSnapshot snapshot = store.stateSnapshot(activeDatabase);
            Optional<OpenBookResult> preexistingOutcome =
                snapshot.state().openBookResult(snapshot.userVersion());
            if (preexistingOutcome.isPresent()) {
              return preexistingOutcome.orElseThrow();
            }

            transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
            SqliteBookSchemaBootstrap.initializeBook(activeDatabase);
            SqliteMutationWriter.insertInitializedAt(activeDatabase, initializedAt);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            store.cacheState(
                new SqliteBookStateSnapshot(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookState.INITIALIZED_FINGRIND));
            return new OpenBookResult.Opened(initializedAt);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to initialize SQLite book.", exception);
          }
        });
  }

  DeclareAccountResult declareAccount(
      AccountCode accountCode,
      AccountName accountName,
      NormalBalance normalBalance,
      Instant declaredAt) {
    store.ensureOpenSession();
    store.accessMode().requireWritableMutation();
    if (Files.notExists(store.bookPath())) {
      return new DeclareAccountResult.Rejected(
          new BookAdministrationRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            if (!store.isInitializedBook(activeDatabase)) {
              return new DeclareAccountResult.Rejected(
                  new BookAdministrationRejection.BookNotInitialized());
            }

            transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
            Optional<DeclaredAccount> existingAccount =
                SqliteStatementQueries.findOneAccount(activeDatabase, accountCode);
            if (existingAccount.isPresent()
                && existingAccount.orElseThrow().normalBalance() != normalBalance) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new DeclareAccountResult.Rejected(
                  new BookAdministrationRejection.NormalBalanceConflict(
                      accountCode, existingAccount.orElseThrow().normalBalance(), normalBalance));
            }

            DeclaredAccount declaredAccount =
                existingAccount
                    .map(
                        account ->
                            new DeclaredAccount(
                                account.accountCode(),
                                accountName,
                                account.normalBalance(),
                                true,
                                declaredAt))
                    .orElseGet(
                        () ->
                            new DeclaredAccount(
                                accountCode, accountName, normalBalance, true, declaredAt));
            SqliteMutationWriter.upsertAccount(activeDatabase, declaredAccount);
            SqliteStoreOperations.commitIfOwned(activeDatabase, transactionOwnership);
            return new DeclareAccountResult.Declared(declaredAccount);
          } catch (SqliteNativeException exception) {
            SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
            throw SqliteStoreOperations.sqliteFailure(
                "Failed to declare SQLite book account.", exception);
          }
        });
  }

  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    store.ensureOpenSession();
    store.accessMode().requireWritableMutation();
    if (Files.notExists(store.bookPath())) {
      return new PostingCommitResult.Rejected(new PostingRejection.BookNotInitialized());
    }
    return withBorrowedDatabase(
        activeDatabase -> {
          SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
          try {
            transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
            Optional<PostingRejection> ordinaryOutcome =
                PostingValidation.rejectionFor(
                    postingDraft,
                    new SqliteTransactionValidationBook(activeDatabase, store.postingReader()));
            if (ordinaryOutcome.isPresent()) {
              SqliteStoreOperations.rollbackIfOwned(activeDatabase, transactionOwnership);
              return new PostingCommitResult.Rejected(ordinaryOutcome.orElseThrow());
            }
            PostingFact postingFact =
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
    store.ensureOpenSession();
    store.accessMode().requireWritableMutation();
    SqliteOwnedPassphrase activeReplacementPassphrase =
        new SqliteOwnedPassphrase(
            Objects.requireNonNull(replacementPassphrase, "replacementPassphrase"));
    try {
      if (Files.notExists(store.bookPath())) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      return withBorrowedDatabase(
          activeDatabase -> {
            if (!store.isInitializedBook(activeDatabase)) {
              return new RekeyBookResult.Rejected(
                  new BookAdministrationRejection.BookNotInitialized());
            }
            SqliteRekeyRollbackFile rollbackFile = SqliteRekeyRollbackFile.create(store.bookPath());
            SqliteNativeConnections.rekey(
                activeDatabase, activeReplacementPassphrase.nativePassphrase());
            try {
              return publishRekeyedDatabase(
                  activeDatabase, activeReplacementPassphrase.nativePassphrase(), rollbackFile);
            } catch (RuntimeException exception) {
              RuntimeException closeFailure =
                  captureBestEffortRuntimeFailure(
                      () -> SqliteStoreContext.closeOwnedDatabase(activeDatabase));
              store.clearDatabaseState();
              RuntimeException restoreFailure =
                  captureBestEffortRuntimeFailure(() -> rollbackFile.restore(store.bookPath()));
              throw finalizeFailedRekey(
                  exception, restoreFailure, closeFailure, rollbackFile::deleteQuietly);
            }
          });
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to rekey SQLite book.", exception);
    } finally {
      activeReplacementPassphrase.close();
    }
  }

  RekeyBookResult publishRekeyedDatabase(
      SqliteNativeDatabase activeDatabase,
      SqliteBookPassphrase replacementPassphrase,
      SqliteRekeyRollbackFile rollbackFile) {
    SqliteNativeDatabase reopenedDatabase = store.openConfiguredDatabase(replacementPassphrase);
    boolean published = false;
    try {
      store.requireInitializedBook(reopenedDatabase);
      SqliteStoreContext.closeOwnedDatabase(activeDatabase);
      store.clearDatabaseState();
      store.publishDatabase(reopenedDatabase);
      published = true;
      rollbackFile.deleteQuietly();
      return new RekeyBookResult.Rekeyed(store.bookPath());
    } finally {
      if (!published) {
        SqliteStoreOperations.closeReopenedDatabaseQuietly(reopenedDatabase);
      }
    }
  }

  private <T> T withBorrowedDatabase(BorrowedDatabaseAction<T> action) {
    return action.run(store.database());
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
    if (closeFailure != null) {
      verificationFailure.addSuppressed(closeFailure);
    }
    return new IllegalStateException(
        "Failed to verify the rekeyed SQLite book. FinGrind restored the pre-rekey book on disk; reopen the session with the original passphrase and retry.",
        verificationFailure);
  }

  /** Runs a best-effort cleanup action that must never replace the primary failure. */
  @FunctionalInterface
  interface BestEffortRuntimeAction {
    /** Executes the cleanup action, reporting but not throwing best-effort runtime failures. */
    void run();
  }
}
