package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Owns connection, transaction, cached state, and terminal-failure lifecycle for one store. */
final class SqliteStoreLifecycle {
  private final Path bookPath;
  private final SqliteStoreAccessMode accessMode;
  private final SqliteBookStateReader bookStateReader;
  private final int bookFormatVersion;
  private final String notInitializedBookMessage;
  private @Nullable SqliteBookPassphrase bookPassphrase;

  private @Nullable SqliteSessionDatabase database;
  private @Nullable SqliteBookStateSnapshot cachedBookState;
  private boolean closed;
  private boolean ledgerPlanTransactionActive;
  private boolean ledgerPlanTransactionBegunInDatabase;
  private @Nullable IllegalStateException terminalFailure;

  SqliteStoreLifecycle(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteStoreAccessMode accessMode,
      SqliteBookStateReader bookStateReader,
      int bookFormatVersion,
      String notInitializedBookMessage) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath");
    this.bookPassphrase = Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.bookStateReader = Objects.requireNonNull(bookStateReader, "bookStateReader");
    this.bookFormatVersion = bookFormatVersion;
    this.notInitializedBookMessage =
        Objects.requireNonNull(notInitializedBookMessage, "notInitializedBookMessage");
  }

  void beginLedgerPlanTransaction() {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (ledgerPlanTransactionActive) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    ledgerPlanTransactionActive = true;
    ledgerPlanTransactionBegunInDatabase = false;
    if (accessMode.preservesMissingBookStateUntilMutation() && Files.notExists(bookPath)) {
      return;
    }
    SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
    try {
      database();
    } catch (IllegalStateException exception) {
      ledgerPlanTransactionActive = false;
      ledgerPlanTransactionBegunInDatabase = false;
      throw exception;
    }
  }

  void commitLedgerPlanTransaction() {
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
    }
  }

  void rollbackLedgerPlanTransaction() {
    if (!ledgerPlanTransactionActive) {
      return;
    }
    ledgerPlanTransactionActive = false;
    boolean rollbackDatabase = ledgerPlanTransactionBegunInDatabase;
    ledgerPlanTransactionBegunInDatabase = false;
    if (rollbackDatabase && database != null) {
      SqliteStoreOperations.rollbackQuietly(database.nativeDatabase());
    }
  }

  void close() {
    if (closed) {
      return;
    }
    try {
      if (database != null) {
        SqliteStoreContext.closeOwnedDatabase(database.nativeDatabase());
      }
      database = null;
      cachedBookState = null;
      closed = true;
    } catch (SqliteNativeException exception) {
      throw SqliteStoreOperations.sqliteFailure(
          "Failed to close SQLite book connection.", exception);
    } finally {
      if (database == null) {
        closePendingPassphrase();
      }
    }
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) {
    return stateSnapshot(activeDatabase).state() == SqliteBookState.INITIALIZED_FINGRIND;
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) {
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    snapshot
        .state()
        .requireInitialized(snapshot.userVersion(), bookFormatVersion, notInitializedBookMessage);
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase) {
    SqliteBookStateSnapshot snapshot = cachedBookState;
    if (snapshot != null) {
      return snapshot;
    }
    snapshot = bookStateReader.snapshot(activeDatabase);
    cachedBookState = snapshot;
    return snapshot;
  }

  SqliteSessionDatabase database() {
    SqliteSessionDatabase activeDatabase = database;
    if (activeDatabase != null) {
      return activeDatabase;
    }
    ContractDecision<SqliteSessionDatabase> openedDatabase = openDatabase();
    return switch (openedDatabase) {
      case ContractDecision.Accepted<SqliteSessionDatabase>(
              SqliteSessionDatabase resolvedDatabase) ->
          resolvedDatabase;
      case ContractDecision.Rejected<SqliteSessionDatabase>(ContractFailure failure) ->
          throw rememberTerminalFailure(new IllegalStateException(failure.message()));
    };
  }

  ContractDecision<SqliteStoreLifecycle> prime() {
    ensureOpen();
    if (database != null) {
      return ContractDecision.accepted(this);
    }
    if (accessMode.preservesMissingBookStateUntilMutation() && Files.notExists(bookPath)) {
      return ContractDecision.accepted(this);
    }
    return openDatabase()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
  }

  private ContractDecision<SqliteSessionDatabase> openDatabase() {
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      SqliteSessionDatabase openedDatabase =
          new SqliteSessionDatabase(
              SqliteConnectionConfigurer.configureOpenedDatabase(
                  SqliteNativeConnections.open(bookPath, passphrase, accessMode.nativeOpenMode()),
                  accessMode));
      database = openedDatabase;
      beginLedgerPlanTransactionIfNeeded(openedDatabase);
      return ContractDecision.accepted(openedDatabase);
    } catch (SqliteNativeException exception) {
      Optional<ContractFailure> authenticationFailure =
          SqliteStoreOperations.authenticationFailure(exception);
      if (authenticationFailure.isPresent()) {
        rememberTerminalFailure(
            new IllegalStateException(authenticationFailure.orElseThrow().message()));
        return ContractDecision.rejected(authenticationFailure.orElseThrow());
      }
      throw rememberTerminalFailure(SqliteStoreOperations.openRuntimeFailure(exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  void ensureOpenSession() {
    ensureOpen();
  }

  SqliteNativeDatabase initializedQueryDatabase() {
    if (Files.notExists(bookPath)) {
      throw new IllegalStateException(notInitializedBookMessage);
    }
    SqliteSessionDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase.nativeDatabase());
    return activeDatabase.nativeDatabase();
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteSessionDatabase activeDatabase) {
    if (ledgerPlanTransactionActive) {
      beginLedgerPlanTransactionIfNeeded(activeDatabase);
      return SqliteTransactionOwnership.SHARED;
    }
    activeDatabase.executeStatement("begin immediate");
    return SqliteTransactionOwnership.OWNED;
  }

  Path bookPath() {
    return bookPath;
  }

  SqliteStoreAccessMode accessMode() {
    return accessMode;
  }

  void cacheState(SqliteBookStateSnapshot snapshot) {
    cachedBookState = Objects.requireNonNull(snapshot, "snapshot");
  }

  void setCachedStateForTesting(@Nullable SqliteBookStateSnapshot snapshot) {
    cachedBookState = snapshot;
  }

  void clearDatabaseState() {
    database = null;
    cachedBookState = null;
  }

  void publishDatabase(SqliteNativeDatabase activeDatabase) {
    database = new SqliteSessionDatabase(Objects.requireNonNull(activeDatabase, "activeDatabase"));
  }

  void publishNativeDatabaseForTesting(@Nullable SqliteNativeDatabase nativeDatabase) {
    database = nativeDatabase == null ? null : new SqliteSessionDatabase(nativeDatabase);
  }

  @Nullable SqliteNativeDatabase currentDatabaseHandle() {
    return database == null ? null : database.nativeDatabase();
  }

  void setPendingPassphraseForTesting(@Nullable SqliteBookPassphrase passphrase) {
    bookPassphrase = passphrase;
  }

  SqliteBookPassphrase takePendingPassphraseForTesting() {
    return takeBookPassphrase();
  }

  boolean closed() {
    return closed;
  }

  boolean ledgerPlanTransactionActive() {
    return ledgerPlanTransactionActive;
  }

  boolean ledgerPlanTransactionBegunInDatabase() {
    return ledgerPlanTransactionBegunInDatabase;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("SQLite book session is already closed.");
    }
    IllegalStateException failure = terminalFailure;
    if (failure != null) {
      throw failure;
    }
  }

  private void beginLedgerPlanTransactionIfNeeded(SqliteSessionDatabase activeDatabase) {
    if (ledgerPlanTransactionActive && !ledgerPlanTransactionBegunInDatabase) {
      activeDatabase.executeStatement("begin immediate");
      ledgerPlanTransactionBegunInDatabase = true;
    }
  }

  private void closePendingPassphrase() {
    if (bookPassphrase == null) {
      return;
    }
    takeBookPassphrase().close();
  }

  private SqliteBookPassphrase takeBookPassphrase() {
    SqliteBookPassphrase passphrase = bookPassphrase;
    bookPassphrase = null;
    if (passphrase == null) {
      throw new IllegalStateException("SQLite book passphrase is no longer available.");
    }
    return passphrase;
  }

  private IllegalStateException rememberTerminalFailure(IllegalStateException failure) {
    terminalFailure = Objects.requireNonNull(failure, "failure");
    return failure;
  }
}
