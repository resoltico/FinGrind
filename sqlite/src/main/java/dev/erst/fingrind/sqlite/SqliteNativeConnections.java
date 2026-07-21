package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
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
  private SqliteNativeConnections() {}

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase) {
    return open(
        bookPath,
        bookPassphrase,
        SqliteNativeOpenMode.READ_WRITE_CREATE,
        SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode) {
    return open(bookPath, bookPassphrase, openMode, SqliteNativeBootstrap.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    SqliteBookFileSecurity.requireRegularNonSymlinkFileIfExists(normalizedBookPath);
    SqliteNativeRuntimeActivity.recordOpeningConnection(
        normalizedBookPath, openMode.publishesActivityMarker());
    boolean connectionRegistrationOpen = true;
    try {
      SqliteBookMaintenanceLease.requireNoActiveLease(normalizedBookPath);
      try (Arena arena = Arena.ofConfined()) {
        MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
        MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
        int resultCode = openNativeDatabase(filename, databasePointer, openMode, sqliteApi);
        MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
        if (resultCode != SqliteNativeResultCode.code("OK")) {
          SqliteNativeException failure = SqliteNativeErrors.failure(resultCode, sqliteApi);
          suppressCloseFailure(databaseHandle, sqliteApi, failure);
          throw failure;
        }
        SqliteNativeDatabase openedDatabase =
            SqliteNativeKeyConfiguration.configureOpenedDatabase(
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
    close(databaseHandle, normalizedBookPath, publishesActivityMarker, true, sqliteApi);
  }

  private static void close(
      MemorySegment databaseHandle,
      @Nullable Path normalizedBookPath,
      boolean publishesActivityMarker,
      boolean recordsConnectionClosure,
      SqliteNativeApi sqliteApi) {
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    SqliteNativeInvocation.runSqlite(
        "Failed to close the SQLite native library bridge.",
        () -> {
          int resultCode =
              SqliteNativeCallAdapter.adapt(
                      SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3CloseV2())
                  .invoke(databaseHandle);
          if (resultCode != SqliteNativeResultCode.code("OK")) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
          if (recordsConnectionClosure) {
            SqliteNativeRuntimeActivity.recordConnectionClosed(
                normalizedBookPath, publishesActivityMarker);
          }
        });
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

  private static int openNativeDatabase(
      MemorySegment filename,
      MemorySegment databasePointer,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to open the SQLite native library bridge.",
        () ->
            SqliteNativeCallAdapter.adapt(
                    SqliteNativeCalls.OpenV2Call.class, sqliteApi.sqlite3OpenV2())
                .invoke(filename, databasePointer, openMode.flags(), MemorySegment.NULL));
  }

  private static void suppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure) {
    if (databaseHandle.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      close(databaseHandle, null, false, false, sqliteApi);
    } catch (RuntimeException exception) {
      primaryFailure.addSuppressed(exception);
    }
  }
}
