package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Owns SQLite native connection open, close, and rekey behavior for the FFM bridge. */
final class SqliteNativeConnections {
  private SqliteNativeConnections() {}

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
    SqliteNativeRuntimeActivity.recordOpeningConnection(
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
        SqliteNativeRuntimeActivity.recordConnectionClosed(
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
          SqliteNativeRuntimeActivity.recordConnectionClosed(
              normalizedBookPath, publishesActivityMarker);
          SqliteNativeBootstrap.shutdownIfQuiescent(
              sqliteApi.sqlite3Shutdown(), SqliteNativeRuntimeActivity.activeConnectionCount());
        });
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase) {
    SqliteNativeKeyConfiguration.rekey(database, bookPassphrase);
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
    return SqliteNativeKeyConfiguration.configureOpenedDatabase(
        normalizedBookPath, databaseHandle, bookPassphrase, sqliteApi, arena);
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      Path normalizedBookPath,
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    return SqliteNativeKeyConfiguration.configureOpenedDatabase(
        normalizedBookPath, databaseHandle, bookPassphrase, openMode, sqliteApi, arena);
  }

  static void requireOpenConfigurationSuccess(int resultCode, SqliteNativeApi sqliteApi) {
    SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(resultCode, sqliteApi);
  }

  static void suppressCloseFailure(
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
    SqliteNativeKeyConfiguration.applyKey(databaseHandle, bookPassphrase, sqliteApi, arena);
  }
}
