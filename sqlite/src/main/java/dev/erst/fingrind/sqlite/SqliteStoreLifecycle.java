package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractFailure;
import dev.erst.fingrind.contract.ContractFailureException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Owns connection, transaction, cached state, and terminal-failure lifecycle for one store. */
final class SqliteStoreLifecycle {
  private final Path bookPath;
  private final SqliteStoreAccessMode accessMode;
  private final SqliteBookStateReader bookStateReader;
  private final int bookFormatVersion;
  private final String notInitializedBookMessage;
  private final Supplier<SqliteNativeApi> sqliteApiSupplier;
  private @Nullable SqliteBookPassphrase bookPassphrase;

  private @Nullable SqliteNativeDatabase database;
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
      String notInitializedBookMessage,
      Supplier<SqliteNativeApi> sqliteApiSupplier) {
    this.bookPath = Objects.requireNonNull(bookPath, "bookPath");
    this.bookPassphrase = Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    this.accessMode = Objects.requireNonNull(accessMode, "accessMode");
    this.bookStateReader = Objects.requireNonNull(bookStateReader, "bookStateReader");
    this.bookFormatVersion = bookFormatVersion;
    this.notInitializedBookMessage =
        Objects.requireNonNull(notInitializedBookMessage, "notInitializedBookMessage");
    this.sqliteApiSupplier = Objects.requireNonNull(sqliteApiSupplier, "sqliteApiSupplier");
  }

  void beginLedgerPlanTransaction() {
    ensureOpen();
    accessMode.requireWritableMutation();
    if (ledgerPlanTransactionActive) {
      throw new IllegalStateException("Ledger plan transaction is already active.");
    }
    ledgerPlanTransactionActive = true;
    ledgerPlanTransactionBegunInDatabase = false;
    if (accessMode.defersMissingBookOpen() && Files.notExists(bookPath)) {
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
    } catch (IllegalStateException exception) {
      throw new IllegalStateException(
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
      SqliteStoreOperations.rollbackQuietly(database);
    }
  }

  void close() {
    if (closed) {
      return;
    }
    try {
      if (database != null) {
        SqliteStoreContext.closeOwnedDatabase(database);
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

  SqliteNativeDatabase database() {
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
    ensureOpen();
    if (database != null) {
      return ContractDecision.accepted(this);
    }
    if (accessMode.defersMissingBookOpen() && Files.notExists(bookPath)) {
      return ContractDecision.accepted(this);
    }
    return openDatabase()
        .fold(ignored -> ContractDecision.accepted(this), ContractDecision::rejected);
  }

  private ContractDecision<SqliteNativeDatabase> openDatabase() {
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      if (accessMode.createsFiles() && Files.notExists(bookPath)) {
        SqliteBookSchemaBootstrap.ensureParentDirectory(bookPath);
      }
      SqliteNativeApi sqliteApi = sqliteApiSupplier.get();
      SqliteNativeDatabase openedDatabase =
          SqliteConnectionConfigurer.configureOpenedDatabase(
              SqliteNativeConnections.open(
                  bookPath, passphrase, accessMode.nativeOpenMode(), sqliteApi),
              accessMode);
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

  void ensureOpenSession() {
    ensureOpen();
  }

  SqliteNativeDatabase initializedQueryDatabase() {
    if (Files.notExists(bookPath)) {
      throw new IllegalStateException(notInitializedBookMessage);
    }
    SqliteNativeDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase);
    return activeDatabase;
  }

  SqliteTransactionOwnership beginImmediateIfNeeded(SqliteNativeDatabase activeDatabase) {
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

  SqliteNativeApi sqliteApi() {
    return sqliteApiSupplier.get();
  }

  void cacheState(SqliteBookStateSnapshot snapshot) {
    cachedBookState = Objects.requireNonNull(snapshot, "snapshot");
  }

  void replaceCachedState(@Nullable SqliteBookStateSnapshot snapshot) {
    cachedBookState = snapshot;
  }

  void clearDatabaseState() {
    database = null;
    cachedBookState = null;
  }

  void publishDatabase(SqliteNativeDatabase activeDatabase) {
    database = Objects.requireNonNull(activeDatabase, "activeDatabase");
  }

  void replaceDatabase(@Nullable SqliteNativeDatabase nativeDatabase) {
    database = nativeDatabase;
  }

  @Nullable SqliteNativeDatabase currentDatabaseHandle() {
    return database;
  }

  void replacePendingPassphrase(@Nullable SqliteBookPassphrase passphrase) {
    bookPassphrase = passphrase;
  }

  SqliteBookPassphrase takePendingPassphrase() {
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

  private void beginLedgerPlanTransactionIfNeeded(SqliteNativeDatabase activeDatabase) {
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
