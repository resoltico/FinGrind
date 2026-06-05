package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractFailure;
import dev.erst.fingrind.contract.runtime.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns the mutable database/session state for one SQLite-backed book session. */
class SqliteStoreLifecycle extends SqliteStoreSessionStateTracker {
  private final SqliteStoreContext context;
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");
  private final SqliteLedgerPlanTransactionCoordinator ledgerPlanTransactions;
  private final Transactions transactions = new Transactions();

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    super(sessionSecret);
    this.context = java.util.Objects.requireNonNull(context, "context");
    this.ledgerPlanTransactions = new SqliteLedgerPlanTransactionCoordinator(context);
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (closed()) {
      return;
    }
    boolean cleanupCreatedBookArtifacts = ledgerPlanTransactions.createdBookArtifacts();
    @Nullable Path preexistingAncestorDirectory =
        ledgerPlanTransactions.preexistingAncestorDirectory();
    try (SqliteStoreCloseSequence closeSequence =
        new SqliteStoreCloseSequence(sessionSecret()::close, publishedDatabase())) {
      java.util.Objects.requireNonNull(closeSequence, "closeSequence");
      markClosed(null);
      // Close sequence runs on scope exit.
    } catch (SqliteNativeException exception) {
      String detail =
          java.util.Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
      SqliteStorageFailureException closeFailure =
          new SqliteStorageFailureException(
              "Failed to close SQLite book connection. " + exception.resultName() + ": " + detail,
              exception);
      markClosed(closeFailure);
      ledgerPlanTransactions.reset();
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      markClosed(closeFailure);
      ledgerPlanTransactions.reset();
      throw closeFailure;
    }
    try {
      if (cleanupCreatedBookArtifacts) {
        SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
            context.bookPath(), preexistingAncestorDirectory, null);
      }
    } finally {
      ledgerPlanTransactions.reset();
    }
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    return stateSnapshot(activeDatabase).state() == SqliteBookState.INITIALIZED_FINGRIND;
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    snapshot
        .state()
        .requireInitialized(
            snapshot.userVersion(),
            context.bookFormatVersion(),
            context.notInitializedBookMessage());
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    SqliteBookStateSnapshot snapshot = cachedBookState();
    if (snapshot != null) {
      return snapshot;
    }
    snapshot = context.bookStateReader().snapshot(activeDatabase);
    cacheState(snapshot);
    return snapshot;
  }

  SqliteNativeDatabase database() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    SqliteNativeDatabase activeDatabase = publishedDatabase();
    if (activeDatabase != null) {
      return activeDatabase;
    }
    ContractDecision<SqliteNativeDatabase> openedDatabase = openDatabase();
    return switch (openedDatabase) {
      case ContractDecision.Accepted<SqliteNativeDatabase>(SqliteNativeDatabase resolvedDatabase) ->
          resolvedDatabase;
      case ContractDecision.Rejected<SqliteNativeDatabase>(ContractFailure failure) ->
          throw rememberedRejectedFailure(failure);
    };
  }

  ContractDecision<SqliteStoreLifecycle> prime() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (publishedDatabase() != null) {
      return ContractDecision.accepted(this);
    }
    if (context.accessMode().defersMissingBookOpen() && Files.notExists(context.bookPath())) {
      return ContractDecision.accepted(this);
    }
    return openDatabase()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
  }

  void ensureOpenSession() {
    threadOwner.requireOwnerThread();
    ensureOpen();
  }

  Transactions transactions() {
    return transactions;
  }

  SqliteNativeDatabase initializedQueryDatabase() {
    threadOwner.requireOwnerThread();
    if (Files.notExists(context.bookPath())) {
      throw new IllegalStateException(context.notInitializedBookMessage());
    }
    SqliteNativeDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase);
    return activeDatabase;
  }

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    threadOwner.requireOwnerThread();
    try (SqliteOwnedPassphrase workingPassphrase = sessionSecret().borrowWorkingCopy()) {
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        ledgerPlanTransactions.noteBookArtifactsMayMutate();
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          context.openConfiguredDatabase(workingPassphrase.nativePassphrase());
      publishDatabase(openedDatabase);
      ledgerPlanTransactions.beginImmediateIfNeeded(openedDatabase);
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteNativeException exception) {
      Optional<ContractFailure> authenticationFailure =
          SqliteStoreOperations.protectedBookVerificationFailure(exception);
      if (authenticationFailure.isPresent()) {
        rememberTerminalFailure(new ContractFailureException(authenticationFailure.orElseThrow()));
        return ContractDecision.rejected(authenticationFailure.orElseThrow());
      }
      throw rememberTerminalFailure(SqliteStoreOperations.openRuntimeFailure(exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  private void cleanupCreatedMissingBookArtifacts(@Nullable Path preexistingAncestorDirectory) {
    SqliteLedgerPlanArtifactCleanup.cleanupCreatedMissingBookArtifacts(
        context.bookPath(), preexistingAncestorDirectory, detachPublishedDatabase());
  }

  /** Coordinates ledger-plan transaction state for one open SQLite store lifecycle. */
  final class Transactions {
    private Transactions() {}

    void begin() {
      threadOwner.requireOwnerThread();
      ensureOpen();
      ledgerPlanTransactions.begin(
          SqliteStoreLifecycle.this::database,
          SqliteStoreLifecycle.this::cleanupCreatedMissingBookArtifacts);
    }

    void commit() {
      threadOwner.requireOwnerThread();
      ensureOpen();
      ledgerPlanTransactions.commit(SqliteStoreLifecycle.this::database);
    }

    void rollback() {
      threadOwner.requireOwnerThread();
      ledgerPlanTransactions.rollback(
          publishedDatabase(), SqliteStoreLifecycle.this::cleanupCreatedMissingBookArtifacts);
    }

    SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
      threadOwner.requireOwnerThread();
      if (ledgerPlanTransactions.active()) {
        ledgerPlanTransactions.beginImmediateIfNeeded(activeDatabase);
        return SqliteTransactionOwnership.SHARED;
      }
      activeDatabase.executeStatement("begin immediate");
      return SqliteTransactionOwnership.OWNED;
    }

    boolean active() {
      threadOwner.requireOwnerThread();
      return ledgerPlanTransactions.active();
    }

    boolean begunInDatabase() {
      threadOwner.requireOwnerThread();
      return ledgerPlanTransactions.begunInDatabase();
    }

    void cleanupCreatedMissingBookArtifactsIfPresent() {
      threadOwner.requireOwnerThread();
      if (!ledgerPlanTransactions.createdBookArtifacts()) {
        return;
      }
      cleanupCreatedMissingBookArtifacts(ledgerPlanTransactions.preexistingAncestorDirectory());
    }
  }
}
