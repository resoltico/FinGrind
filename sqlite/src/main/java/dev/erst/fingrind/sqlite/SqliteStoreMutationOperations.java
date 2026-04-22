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

/** Mutation operations over one SQLite-backed book session. */
final class SqliteStoreMutationOperations {
  private final SqliteStoreContext store;

  SqliteStoreMutationOperations(SqliteStoreContext store) {
    this.store = Objects.requireNonNull(store, "store");
  }

  OpenBookResult openBook(Instant initializedAt) {
    store.ensureOpenSession();
    store.accessMode().requireWritableInitialization();
    SqliteBookSchemaBootstrap.ensureParentDirectory(store.bookPath());
    SqliteSessionDatabase activeDatabase = store.database();
    SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
    try {
      SqliteBookStateSnapshot snapshot = store.stateSnapshot(activeDatabase.nativeDatabase());
      Optional<OpenBookResult> preexistingOutcome =
          snapshot.state().openBookResult(snapshot.userVersion());
      if (preexistingOutcome.isPresent()) {
        return preexistingOutcome.orElseThrow();
      }

      transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
      SqliteBookSchemaBootstrap.initializeBook(activeDatabase.nativeDatabase());
      SqliteMutationWriter.insertInitializedAt(activeDatabase.nativeDatabase(), initializedAt);
      SqliteStoreOperations.commitIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      store.cacheState(
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookState.INITIALIZED_FINGRIND));
      return new OpenBookResult.Opened(initializedAt);
    } catch (SqliteNativeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      throw SqliteStoreOperations.sqliteFailure("Failed to initialize SQLite book.", exception);
    }
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
    SqliteSessionDatabase activeDatabase = store.database();
    SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
    try {
      if (!store.isInitializedBook(activeDatabase.nativeDatabase())) {
        return new DeclareAccountResult.Rejected(
            new BookAdministrationRejection.BookNotInitialized());
      }

      transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
      Optional<DeclaredAccount> existingAccount =
          SqliteStatementQueries.findOneAccount(activeDatabase.nativeDatabase(), accountCode);
      if (existingAccount.isPresent()
          && existingAccount.orElseThrow().normalBalance() != normalBalance) {
        SqliteStoreOperations.rollbackIfOwned(
            activeDatabase.nativeDatabase(), transactionOwnership);
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
                          account.declaredAt()))
              .orElseGet(
                  () ->
                      new DeclaredAccount(
                          accountCode, accountName, normalBalance, true, declaredAt));
      SqliteMutationWriter.upsertAccount(activeDatabase.nativeDatabase(), declaredAccount);
      SqliteStoreOperations.commitIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      return new DeclareAccountResult.Declared(declaredAccount);
    } catch (SqliteNativeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to declare SQLite book account.", exception);
    }
  }

  PostingCommitResult commit(PostingDraft postingDraft, PostingIdGenerator postingIdGenerator) {
    store.ensureOpenSession();
    store.accessMode().requireWritableMutation();
    if (Files.notExists(store.bookPath())) {
      return new PostingCommitResult.Rejected(new PostingRejection.BookNotInitialized());
    }
    SqliteSessionDatabase activeDatabase = store.database();
    SqliteTransactionOwnership transactionOwnership = SqliteTransactionOwnership.SHARED;
    try {
      transactionOwnership = store.beginImmediateIfNeeded(activeDatabase);
      Optional<PostingRejection> ordinaryOutcome =
          PostingValidation.rejectionFor(
              postingDraft,
              new SqliteTransactionValidationBook(
                  activeDatabase.nativeDatabase(), store.postingReader()));
      if (ordinaryOutcome.isPresent()) {
        SqliteStoreOperations.rollbackIfOwned(
            activeDatabase.nativeDatabase(), transactionOwnership);
        return new PostingCommitResult.Rejected(ordinaryOutcome.orElseThrow());
      }
      PostingFact postingFact =
          postingDraft.materialize(
              Objects.requireNonNull(postingIdGenerator, "postingIdGenerator").nextPostingId());
      SqliteMutationWriter.insertPostingFact(activeDatabase.nativeDatabase(), postingFact);
      SqliteMutationWriter.insertJournalLines(activeDatabase.nativeDatabase(), postingFact);
      SqliteStoreOperations.commitIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      return new PostingCommitResult.Committed(postingFact);
    } catch (SqliteNativeException exception) {
      SqliteStoreOperations.rollbackIfOwned(activeDatabase.nativeDatabase(), transactionOwnership);
      throw SqliteStoreOperations.sqliteFailure("Failed to commit SQLite posting fact.", exception);
    }
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
      SqliteSessionDatabase activeDatabase = store.database();
      if (!store.isInitializedBook(activeDatabase.nativeDatabase())) {
        return new RekeyBookResult.Rejected(new BookAdministrationRejection.BookNotInitialized());
      }
      SqliteNativeConnections.rekey(
          activeDatabase.nativeDatabase(), activeReplacementPassphrase.nativePassphrase());
      SqliteStoreContext.closeOwnedDatabase(activeDatabase.nativeDatabase());
      store.clearDatabaseState();
      SqliteSessionDatabase reopenedDatabase = null;
      try {
        reopenedDatabase =
            new SqliteSessionDatabase(
                SqliteConnectionConfigurer.configureOpenedDatabase(
                    SqliteNativeConnections.open(
                        store.bookPath(),
                        activeReplacementPassphrase.nativePassphrase(),
                        store.accessMode().nativeOpenMode()),
                    store.accessMode()));
        store.requireInitializedBook(reopenedDatabase.nativeDatabase());
        store.publishDatabase(reopenedDatabase.nativeDatabase());
        return new RekeyBookResult.Rekeyed(store.bookPath());
      } catch (RuntimeException exception) {
        SqliteStoreOperations.closeReopenedDatabaseQuietly(reopenedDatabase);
        throw exception;
      }
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure("Failed to rekey SQLite book.", exception);
    } finally {
      activeReplacementPassphrase.close();
    }
  }
}
