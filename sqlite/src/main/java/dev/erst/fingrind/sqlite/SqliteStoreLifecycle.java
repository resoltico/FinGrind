package dev.erst.fingrind.sqlite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
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
    SqliteSchemaManager.ensureParentDirectory(bookPath);
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
      throw SqliteStoreSupport.sqliteFailure(
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
      SqliteStoreSupport.rollbackQuietly(database.nativeDatabase());
    }
  }

  void close() {
    if (closed) {
      return;
    }
    try {
      if (database != null) {
        SqlitePostingFactStore.closeOwnedDatabase(database.nativeDatabase());
      }
      database = null;
      cachedBookState = null;
      closed = true;
    } catch (SqliteNativeException exception) {
      throw SqliteStoreSupport.sqliteFailure("Failed to close SQLite book connection.", exception);
    } finally {
      if (database == null) {
        closePendingPassphrase();
      }
    }
  }

  boolean isInitializedBook(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    return stateSnapshot(activeDatabase).state() == SqliteBookState.INITIALIZED_FINGRIND;
  }

  void requireInitializedBook(SqliteNativeDatabase activeDatabase) throws SqliteNativeException {
    SqliteBookStateSnapshot snapshot = stateSnapshot(activeDatabase);
    snapshot
        .state()
        .requireInitialized(snapshot.userVersion(), bookFormatVersion, notInitializedBookMessage);
  }

  SqliteBookStateSnapshot stateSnapshot(SqliteNativeDatabase activeDatabase)
      throws SqliteNativeException {
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
    try (SqliteBookPassphrase passphrase = takeBookPassphrase()) {
      database =
          new SqliteSessionDatabase(
              SqliteConnectionSupport.configureOpenedDatabase(
                  SqliteNativeLibrary.open(bookPath, passphrase, accessMode.nativeOpenMode()),
                  accessMode));
      beginLedgerPlanTransactionIfNeeded(database);
      return Objects.requireNonNull(database, "database");
    } catch (SqliteNativeException exception) {
      throw rememberTerminalFailure(SqliteStoreSupport.openFailure(exception));
    } catch (IllegalStateException exception) {
      throw rememberTerminalFailure(exception);
    }
  }

  void ensureOpenSession() {
    ensureOpen();
  }

  SqliteNativeDatabase initializedQueryDatabase() throws SqliteNativeException {
    if (Files.notExists(bookPath)) {
      throw new IllegalStateException(notInitializedBookMessage);
    }
    SqliteSessionDatabase activeDatabase = database();
    requireInitializedBook(activeDatabase.nativeDatabase());
    return activeDatabase.nativeDatabase();
  }

  boolean beginImmediateIfNeeded(SqliteSessionDatabase activeDatabase)
      throws SqliteNativeException {
    if (ledgerPlanTransactionActive) {
      beginLedgerPlanTransactionIfNeeded(activeDatabase);
      return false;
    }
    activeDatabase.executeStatement("begin immediate");
    return true;
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

  private void beginLedgerPlanTransactionIfNeeded(SqliteSessionDatabase activeDatabase)
      throws SqliteNativeException {
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
