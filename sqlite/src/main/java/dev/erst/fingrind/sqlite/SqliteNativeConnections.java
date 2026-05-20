package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns SQLite native connection open, close, and rekey behavior for the FFM bridge. */
final class SqliteNativeConnections {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  private static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

  private SqliteNativeConnections() {}

  static SqliteNativeDatabase openKeyFileAccess(Path bookPath, Path keyFilePath) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(keyFilePath, "keyFilePath");
    return SqliteBookKeyFile.loadDecision(keyFilePath)
        .fold(
            bookPassphrase -> {
              try (bookPassphrase) {
                return open(bookPath, bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
              }
            },
            failure -> {
              throw new ContractFailureException(failure);
            });
  }

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(
        bookPath,
        bookPassphrase,
        SqliteNativeOpenMode.READ_WRITE_CREATE,
        RollbackArtifactWarningPolicy.REPORT_STALE_ARTIFACTS,
        SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode) {
    return open(
        bookPath,
        bookPassphrase,
        openMode,
        RollbackArtifactWarningPolicy.REPORT_STALE_ARTIFACTS,
        SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase openWithoutRollbackArtifactWarning(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode) {
    return openWithoutRollbackArtifactWarning(
        bookPath, bookPassphrase, openMode, SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase openWithoutRollbackArtifactWarning(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return open(
        bookPath,
        bookPassphrase,
        openMode,
        RollbackArtifactWarningPolicy.SUPPRESS_STALE_ARTIFACTS,
        sqliteApi);
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return open(
        bookPath,
        bookPassphrase,
        openMode,
        RollbackArtifactWarningPolicy.REPORT_STALE_ARTIFACTS,
        sqliteApi);
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      RollbackArtifactWarningPolicy rollbackArtifactWarningPolicy,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(rollbackArtifactWarningPolicy, "rollbackArtifactWarningPolicy");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    SqliteBookFileSecurity.requireRegularNonSymlinkFileIfExists(normalizedBookPath);
    SqliteNativeBootstrap.recordOpeningConnection(
        normalizedBookPath, openMode.publishesActivityMarker());
    boolean connectionRegistrationOpen = true;
    try {
      SqliteBookMaintenanceLease.requireNoActiveLease(normalizedBookPath);
      rollbackArtifactWarningPolicy.reportIfNeeded(normalizedBookPath);
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
        int resultCode = openNativeDatabase(filename, databasePointer, openMode, sqliteApi);
        MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
        if (resultCode != SqliteNativeResultCodes.OK) {
          SqliteNativeException failure = SqliteNativeErrors.failure(resultCode, sqliteApi);
          suppressCloseFailure(databaseHandle, sqliteApi, failure);
          throw failure;
        }
        SqliteNativeDatabase openedDatabase =
            configureOpenedDatabase(
                normalizedBookPath, databaseHandle, bookPassphrase, openMode, sqliteApi, arena);
        connectionRegistrationOpen = false;
        return hardenOpenedDatabase(normalizedBookPath, openedDatabase, openMode);
      }
    } finally {
      if (connectionRegistrationOpen) {
        SqliteNativeBootstrap.recordConnectionClosed(
            normalizedBookPath, openMode.publishesActivityMarker());
      }
    }
  }

  private static SqliteNativeDatabase hardenOpenedDatabase(
      Path normalizedBookPath, SqliteNativeDatabase openedDatabase, SqliteNativeOpenMode openMode) {
    try {
      if (openMode.hardensBookArtifactsOnOpen()) {
        enforceBookFilePermissions(normalizedBookPath, SqliteBookFileSecurity::hardenBookArtifacts);
      }
      return openedDatabase;
    } catch (RuntimeException | Error exception) {
      try {
        openedDatabase.close();
      } catch (RuntimeException closeFailure) {
        exception.addSuppressed(closeFailure);
      }
      throw exception;
    }
  }

  static void close(
      MemorySegment databaseHandle, @Nullable Path normalizedBookPath, SqliteNativeApi sqliteApi) {
    close(databaseHandle, normalizedBookPath, true, sqliteApi);
  }

  static void close(
      MemorySegment databaseHandle,
      @Nullable Path normalizedBookPath,
      boolean publishesActivityMarker,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    SqliteNativeInvocation.runSqlite(
        "Failed to close the SQLite native library bridge.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressToInt(sqliteApi.sqlite3CloseV2()).invoke(databaseHandle);
          if (resultCode != SqliteNativeResultCodes.OK) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
          SqliteNativeBootstrap.recordConnectionClosed(normalizedBookPath, publishesActivityMarker);
          SqliteNativeBootstrap.shutdownIfQuiescent(
              sqliteApi.sqlite3Shutdown(), SqliteNativeBootstrap.activeConnectionCount());
        });
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase) {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    SqliteNativeApi sqliteApi = database.sqliteApi();
    try (Arena arena = Arena.ofConfined()) {
      try (SqliteNativeSecretBuffer keyBuffer =
          SqliteNativeSecretBuffer.cString(bookPassphrase, arena)) {
        int resultCode =
            SqliteNativeInvocation.invokeSqlite(
                "Failed to rekey the FinGrind SQLite book with passphrase material from "
                    + bookPassphrase.sourceDescription()
                    + ".",
                () ->
                    SqliteNativeCalls.addressAddressIntToInt(sqliteApi.sqlite3Rekey())
                        .invoke(
                            database.handle(), keyBuffer.pointer(), bookPassphrase.byteLength()));
        if (resultCode != SqliteNativeResultCodes.OK) {
          throw SqliteNativeErrors.failure(resultCode, sqliteApi);
        }
      }
      validateConfiguredKey(database.handle(), sqliteApi);
    }
  }

  static void enforceBookFilePermissions(
      Path normalizedBookPath, SqliteBookArtifactHardener artifactHardener) {
    try {
      artifactHardener.harden(normalizedBookPath);
    } catch (IOException exception) {
      throw new SqliteStorageFailureException(
          "Failed to enforce the FinGrind SQLite book file permissions.", exception);
    }
  }

  /** Applies the repository's book-artifact permission policy to a normalized SQLite path. */
  @FunctionalInterface
  interface SqliteBookArtifactHardener {
    /** Hardens the SQLite book file and sidecar artifacts rooted at the given normalized path. */
    void harden(Path normalizedBookPath) throws IOException;
  }

  /** Policy for whether one native-open call reports sibling rekey rollback artifacts. */
  enum RollbackArtifactWarningPolicy {
    REPORT_STALE_ARTIFACTS {
      @Override
      void reportIfNeeded(Path normalizedBookPath) {
        SqliteRekeyRollbackFile.reportStaleRollbackArtifacts(normalizedBookPath);
      }
    },
    SUPPRESS_STALE_ARTIFACTS {
      @Override
      void reportIfNeeded(Path normalizedBookPath) {
        Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
      }
    };

    abstract void reportIfNeeded(Path normalizedBookPath);
  }

  private static int openNativeDatabase(
      MemorySegment filename,
      MemorySegment databasePointer,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to open the SQLite native library bridge.",
        () ->
            SqliteNativeCalls.openV2(sqliteApi.sqlite3OpenV2())
                .invoke(filename, databasePointer, openMode.flags(), MemorySegment.NULL));
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      Path normalizedBookPath,
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    return configureOpenedDatabase(
        normalizedBookPath,
        databaseHandle,
        bookPassphrase,
        SqliteNativeOpenMode.READ_WRITE_CREATE,
        sqliteApi,
        arena);
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      Path normalizedBookPath,
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    try {
      applyKey(databaseHandle, bookPassphrase, sqliteApi, arena);
      int timeoutResult =
          SqliteNativeInvocation.invoke(
              "Failed to open the SQLite native library bridge.",
              () ->
                  SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3BusyTimeout())
                      .invoke(databaseHandle, SQLITE_BUSY_TIMEOUT_MILLIS));
      requireOpenConfigurationSuccess(timeoutResult, sqliteApi);
      int extendedCodeResult =
          SqliteNativeInvocation.invoke(
              "Failed to open the SQLite native library bridge.",
              () ->
                  SqliteNativeCalls.addressIntToInt(sqliteApi.sqlite3ExtendedResultCodes())
                      .invoke(databaseHandle, 1));
      requireOpenConfigurationSuccess(extendedCodeResult, sqliteApi);
      validateConfiguredKey(databaseHandle, sqliteApi);
      return new SqliteNativeDatabase(
          databaseHandle, normalizedBookPath, openMode.publishesActivityMarker(), sqliteApi);
    } catch (SqliteNativeException exception) {
      suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    } catch (Error error) {
      suppressCloseFailure(databaseHandle, sqliteApi, error);
      throw error;
    } catch (RuntimeException exception) {
      suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    }
  }

  static void requireOpenConfigurationSuccess(int resultCode, SqliteNativeApi sqliteApi) {
    if (resultCode != SqliteNativeResultCodes.OK) {
      throw SqliteNativeErrors.failure(resultCode, sqliteApi);
    }
  }

  private static void suppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure) {
    if (databaseHandle.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      int resultCode =
          SqliteNativeCalls.addressToInt(sqliteApi.sqlite3CloseV2()).invoke(databaseHandle);
      if (resultCode != SqliteNativeResultCodes.OK) {
        primaryFailure.addSuppressed(SqliteNativeErrors.failure(resultCode, sqliteApi));
      }
    } catch (RuntimeException exception) {
      primaryFailure.addSuppressed(exception);
    }
  }

  static void applyKey(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    SqliteNativeInvocation.runSqlite(
        "Failed to apply the FinGrind SQLite book passphrase from "
            + bookPassphrase.sourceDescription()
            + ".",
        () -> {
          try (SqliteNativeSecretBuffer keyBuffer =
              SqliteNativeSecretBuffer.cString(bookPassphrase, arena)) {
            int resultCode =
                SqliteNativeCalls.addressAddressIntToInt(sqliteApi.sqlite3Key())
                    .invoke(databaseHandle, keyBuffer.pointer(), bookPassphrase.byteLength());
            requireOpenConfigurationSuccess(resultCode, sqliteApi);
          }
        });
  }

  private static void validateConfiguredKey(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(
          databaseHandle, arena.allocateFrom(KEY_VALIDATION_QUERY), sqliteApi);
    }
  }
}
