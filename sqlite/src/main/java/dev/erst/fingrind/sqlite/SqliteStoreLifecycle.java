package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
import java.nio.file.Files;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns the mutable database/session state for one SQLite-backed book session. */
class SqliteStoreLifecycle {
  private final SqliteStoreContext context;
  private final SqliteSessionSecret sessionSecret;
  private final SqliteThreadOwner threadOwner = new SqliteThreadOwner("SQLite book session");

  private @Nullable SqliteNativeDatabase database;
  private @Nullable SqliteBookStateSnapshot cachedBookState;
  private boolean closed;
  private boolean ledgerPlanTransactionActive;
  private boolean ledgerPlanTransactionBegunInDatabase;
  private @Nullable IllegalStateException terminalFailure;

  SqliteStoreLifecycle(SqliteStoreContext context, SqliteSessionSecret sessionSecret) {
    this.context = Objects.requireNonNull(context, "context");
    this.sessionSecret = Objects.requireNonNull(sessionSecret, "sessionSecret");
  }

  void beginLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    context.accessMode().requireWritableMutation();
    if (ledgerPlanTransactionActive) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    ledgerPlanTransactionActive = true;
    ledgerPlanTransactionBegunInDatabase = false;
    if (context.accessMode().defersMissingBookOpen() && Files.notExists(context.bookPath())) {
      return;
    }
    SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
    try {
      database();
    } catch (IllegalStateException exception) {
      ledgerPlanTransactionActive = false;
      ledgerPlanTransactionBegunInDatabase = false;
      throw exception;
    }
  }

  void commitLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (!ledgerPlanTransactionActive) {
      throw new IllegalStateException("No ledger plan transaction is active.");
    }
    if (!ledgerPlanTransactionBegunInDatabase) {
      ledgerPlanTransactionActive = false;
      return;
    }
    try {
      database().executeStatement("commit");
      ledgerPlanTransactionActive = false;
      ledgerPlanTransactionBegunInDatabase = false;
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to commit SQLite ledger plan transaction.", exception);
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
          "Failed to commit SQLite ledger plan transaction.", exception);
    }
  }

  void rollbackLedgerPlanTransaction() {
    threadOwner.requireOwnerThread();
    if (!ledgerPlanTransactionActive) {
      return;
    }
    ledgerPlanTransactionActive = false;
    boolean rollbackDatabase = ledgerPlanTransactionBegunInDatabase;
    ledgerPlanTransactionBegunInDatabase = false;
    if (rollbackDatabase && database != null) {
      SqliteStoreOperations.rollbackQuietly(database);
    }
  }

  void close() {
    threadOwner.requireOwnerThread();
    if (closed) {
      return;
    }
    SqliteNativeDatabase closingDatabase = database;
    SqliteSessionSecret closingSecret = sessionSecret;
    database = null;
    cachedBookState = null;
    ledgerPlanTransactionActive = false;
    ledgerPlanTransactionBegunInDatabase = false;
    closed = true;
    try (closingSecret;
        closingDatabase) {
      // Resources close on scope exit; any close failure poisons this session permanently.
    } catch (SqliteNativeException exception) {
      String detail = Objects.requireNonNullElse(exception.getMessage(), "SQLite native failure.");
      SqliteStorageFailureException closeFailure =
          new SqliteStorageFailureException(
              "Failed to close SQLite book connection. " + exception.resultName() + ": " + detail,
              exception);
      terminalFailure = closeFailure;
      throw closeFailure;
    } catch (IllegalStateException exception) {
      IllegalStateException closeFailure =
          new IllegalStateException("Failed to close SQLite book connection.", exception);
      terminalFailure = closeFailure;
      throw closeFailure;
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
    SqliteBookStateSnapshot snapshot = cachedBookState;
    if (snapshot != null) {
      return snapshot;
    }
    snapshot = context.bookStateReader().snapshot(activeDatabase);
    cachedBookState = snapshot;
    return snapshot;
  }

  SqliteNativeDatabase database() {
    threadOwner.requireOwnerThread();
    SqliteNativeDatabase activeDatabase = database;
    if (activeDatabase != null) {
      return activeDatabase;
    }
    ContractDecision<SqliteNativeDatabase> openedDatabase = openDatabase();
    return switch (openedDatabase) {
      case ContractDecision.Accepted<SqliteNativeDatabase>(SqliteNativeDatabase resolvedDatabase) ->
          resolvedDatabase;
      case ContractDecision.Rejected<SqliteNativeDatabase>(ContractFailure failure) ->
          throw rememberTerminalFailure(new ContractFailureException(failure));
    };
  }

  ContractDecision<SqliteStoreLifecycle> prime() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    if (database != null) {
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

  SqliteNativeDatabase initializedQueryDatabase() {
    threadOwner.requireOwnerThread();
    if (Files.notExists(context.bookPath())) {
      throw new IllegalStateException(context.notInitializedBookMessage());
    }
    SqliteNativeDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase);
    return activeDatabase;
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    if (ledgerPlanTransactionActive) {
      beginLedgerPlanTransactionIfNeeded(activeDatabase);
      return SqliteTransactionOwnership.SHARED;
    }
    activeDatabase.executeStatement("begin immediate");
    return SqliteTransactionOwnership.OWNED;
  }

  void cacheState(SqliteBookStateSnapshot snapshot) {
    threadOwner.requireOwnerThread();
    cachedBookState = Objects.requireNonNull(snapshot, "snapshot");
  }

  void clearCachedState() {
    threadOwner.requireOwnerThread();
    cachedBookState = null;
  }

  void clearDatabaseState() {
    threadOwner.requireOwnerThread();
    database = null;
    cachedBookState = null;
  }

  void publishDatabase(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    database = Objects.requireNonNull(activeDatabase, "activeDatabase");
  }

  void rotateSessionSecret(SqliteBookPassphrase replacementPassphrase) {
    threadOwner.requireOwnerThread();
    sessionSecret.rotateTo(replacementPassphrase);
  }

  @Nullable SqliteNativeDatabase publishedDatabase() {
    threadOwner.requireOwnerThread();
    return database;
  }

  boolean closed() {
    threadOwner.requireOwnerThread();
    return closed;
  }

  boolean ledgerPlanTransactionActive() {
    threadOwner.requireOwnerThread();
    return ledgerPlanTransactionActive;
  }

  boolean ledgerPlanTransactionBegunInDatabase() {
    threadOwner.requireOwnerThread();
    return ledgerPlanTransactionBegunInDatabase;
  }

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    threadOwner.requireOwnerThread();
    try (SqliteOwnedPassphrase workingPassphrase = sessionSecret.borrowWorkingCopy()) {
      if (context.accessMode().createsFiles() && Files.notExists(context.bookPath())) {
        SqliteBookSchemaBootstrap.ensureParentDirectory(context.bookPath());
      }
      SqliteNativeDatabase openedDatabase =
          context.openConfiguredDatabase(workingPassphrase.nativePassphrase());
      database = openedDatabase;
      beginLedgerPlanTransactionIfNeeded(openedDatabase);
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteNativeException exception) {
      Optional<ContractFailure> authenticationFailure =
          SqliteStoreOperations.authenticationFailure(exception);
      if (authenticationFailure.isPresent()) {
        rememberTerminalFailure(new ContractFailureException(authenticationFailure.orElseThrow()));
        return ContractDecision.rejected(authenticationFailure.orElseThrow());
      }
      throw rememberTerminalFailure(SqliteStoreOperations.openRuntimeFailure(exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  private void ensureOpen() {
    threadOwner.requireOwnerThread();
    IllegalStateException failure = terminalFailure;
    if (failure != null) {
      throw failure;
    }
    if (closed) {
      throw new IllegalStateException("SQLite book session is already closed.");
    }
  }

  private void beginLedgerPlanTransactionIfNeeded(SqliteNativeDatabase activeDatabase) {
    threadOwner.requireOwnerThread();
    if (ledgerPlanTransactionActive && !ledgerPlanTransactionBegunInDatabase) {
      activeDatabase.executeStatement("begin immediate");
      ledgerPlanTransactionBegunInDatabase = true;
    }
  }

  private IllegalStateException rememberTerminalFailure(IllegalStateException failure) {
    terminalFailure = Objects.requireNonNull(failure, "failure");
    return failure;
  }
}
