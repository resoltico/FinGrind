package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Owns SQLite native connection open, close, and rekey behavior for the FFM bridge. */
final class SqliteNativeConnections {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  private static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";
  private static final AtomicReference<MethodHandle> SQLITE3_OPEN_V2_OVERRIDE =
      new AtomicReference<>();
  private static final AtomicReference<MethodHandle> SQLITE3_CLOSE_V2_OVERRIDE =
      new AtomicReference<>();
  private static final AtomicReference<MethodHandle> SQLITE3_REKEY_OVERRIDE =
      new AtomicReference<>();

  private SqliteNativeConnections() {}

  static SqliteNativeDatabase open(BookAccess bookAccess) throws SqliteNativeException {
    Objects.requireNonNull(bookAccess, "bookAccess");
    if (!(bookAccess.passphraseSource() instanceof BookAccess.PassphraseSource.KeyFile keyFile)) {
      throw new IllegalArgumentException(
          "SQLite same-package file-backed open requires a --book-key-file access selection.");
    }
    try (SqliteBookPassphrase bookPassphrase = SqliteBookKeyFile.load(keyFile.bookKeyFilePath())) {
      return open(
          bookAccess.bookFilePath(), bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
    }
  }

  static SqliteNativeDatabase open(Path bookPath, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    return open(bookPath, bookPassphrase, SqliteNativeOpenMode.READ_WRITE_CREATE);
  }

  static SqliteNativeDatabase open(
      Path bookPath, SqliteBookPassphrase bookPassphrase, SqliteNativeOpenMode openMode)
      throws SqliteNativeException {
    return open(bookPath, bookPassphrase, openMode, SqliteNativeLibrary.api());
  }

  static SqliteNativeDatabase open(
      Path bookPath,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi)
      throws SqliteNativeException {
    Objects.requireNonNull(bookPath, "bookPath");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    Objects.requireNonNull(openMode, "openMode");
    Objects.requireNonNull(sqliteApi, "sqliteApi");
    Path normalizedBookPath = bookPath.toAbsolutePath().normalize();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment databasePointer = arena.allocate(ValueLayout.ADDRESS);
      MemorySegment filename = arena.allocateFrom(normalizedBookPath.toString());
      int resultCode = openNativeDatabase(filename, databasePointer, openMode, sqliteApi);
      MemorySegment databaseHandle = databasePointer.get(ValueLayout.ADDRESS, 0);
      if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
        SqliteNativeException failure = SqliteNativeErrors.failure(resultCode, sqliteApi);
        suppressCloseFailure(databaseHandle, sqliteApi, failure);
        throw failure;
      }
      return configureOpenedDatabase(databaseHandle, bookPassphrase, sqliteApi, arena);
    }
  }

  static void close(MemorySegment databaseHandle) throws SqliteNativeException {
    SqliteNativeApi sqliteApi = SqliteNativeLibrary.api();
    SqliteNativeInvocation.runSqlite(
        "Failed to close the SQLite native library bridge.",
        () -> {
          int resultCode =
              SqliteNativeCalls.addressToInt(effectiveSqlite3CloseV2(sqliteApi))
                  .invoke(databaseHandle);
          if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
            throw SqliteNativeErrors.failure(resultCode, sqliteApi);
          }
          SqliteNativeBootstrap.recordClosedConnection();
          SqliteNativeBootstrap.shutdownIfQuiescent(
              sqliteApi.sqlite3Shutdown(), SqliteNativeBootstrap.activeConnectionCount());
        });
  }

  static void rekey(SqliteNativeDatabase database, SqliteBookPassphrase bookPassphrase)
      throws SqliteNativeException {
    Objects.requireNonNull(database, "database");
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    SqliteNativeApi sqliteApi = SqliteNativeLibrary.api();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to rekey the FinGrind SQLite book with passphrase material from "
                  + bookPassphrase.sourceDescription()
                  + ".",
              () ->
                  SqliteNativeCalls.addressAddressIntToInt(effectiveSqlite3Rekey(sqliteApi))
                      .invoke(database.handle(), keyPointer, bookPassphrase.byteLength()));
      if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
        throw SqliteNativeErrors.failure(resultCode, sqliteApi);
      }
      validateConfiguredKey(database.handle(), sqliteApi);
    }
  }

  static AutoCloseable overrideSqlite3RekeyHandleForTesting(MethodHandle sqlite3RekeyHandle) {
    Objects.requireNonNull(sqlite3RekeyHandle, "sqlite3RekeyHandle");
    MethodHandle previousHandle = SQLITE3_REKEY_OVERRIDE.getAndSet(sqlite3RekeyHandle);
    return () -> SQLITE3_REKEY_OVERRIDE.set(previousHandle);
  }

  static AutoCloseable overrideSqlite3OpenV2HandleForTesting(MethodHandle sqlite3OpenV2Handle) {
    Objects.requireNonNull(sqlite3OpenV2Handle, "sqlite3OpenV2Handle");
    MethodHandle previousHandle = SQLITE3_OPEN_V2_OVERRIDE.getAndSet(sqlite3OpenV2Handle);
    return () -> SQLITE3_OPEN_V2_OVERRIDE.set(previousHandle);
  }

  static AutoCloseable overrideSqlite3CloseV2HandleForTesting(MethodHandle sqlite3CloseV2Handle) {
    Objects.requireNonNull(sqlite3CloseV2Handle, "sqlite3CloseV2Handle");
    MethodHandle previousHandle = SQLITE3_CLOSE_V2_OVERRIDE.getAndSet(sqlite3CloseV2Handle);
    return () -> SQLITE3_CLOSE_V2_OVERRIDE.set(previousHandle);
  }

  private static int openNativeDatabase(
      MemorySegment filename,
      MemorySegment databasePointer,
      SqliteNativeOpenMode openMode,
      SqliteNativeApi sqliteApi) {
    return SqliteNativeInvocation.invoke(
        "Failed to open the SQLite native library bridge.",
        () ->
            SqliteNativeCalls.openV2(effectiveSqlite3OpenV2(sqliteApi))
                .invoke(filename, databasePointer, openMode.flags(), MemorySegment.NULL));
  }

  static SqliteNativeDatabase configureOpenedDatabase(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      SqliteNativeApi sqliteApi,
      Arena arena)
      throws SqliteNativeException {
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
      SqliteNativeBootstrap.recordOpenedConnection();
      return new SqliteNativeDatabase(databaseHandle);
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

  private static MethodHandle effectiveSqlite3OpenV2(SqliteNativeApi sqliteApi) {
    return Objects.requireNonNullElseGet(SQLITE3_OPEN_V2_OVERRIDE.get(), sqliteApi::sqlite3OpenV2);
  }

  private static MethodHandle effectiveSqlite3CloseV2(SqliteNativeApi sqliteApi) {
    return Objects.requireNonNullElseGet(
        SQLITE3_CLOSE_V2_OVERRIDE.get(), sqliteApi::sqlite3CloseV2);
  }

  private static MethodHandle effectiveSqlite3Rekey(SqliteNativeApi sqliteApi) {
    return Objects.requireNonNullElseGet(SQLITE3_REKEY_OVERRIDE.get(), sqliteApi::sqlite3Rekey);
  }

  static void requireOpenConfigurationSuccess(int resultCode, SqliteNativeApi sqliteApi)
      throws SqliteNativeException {
    if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
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
      if (resultCode != SqliteNativeLibrary.SQLITE_OK) {
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
      Arena arena)
      throws SqliteNativeException {
    SqliteNativeInvocation.runSqlite(
        "Failed to apply the FinGrind SQLite book passphrase from "
            + bookPassphrase.sourceDescription()
            + ".",
        () -> {
          MemorySegment keyPointer = bookPassphrase.copyToCString(arena);
          int resultCode =
              SqliteNativeCalls.addressAddressIntToInt(sqliteApi.sqlite3Key())
                  .invoke(databaseHandle, keyPointer, bookPassphrase.byteLength());
          requireOpenConfigurationSuccess(resultCode, sqliteApi);
        });
  }

  private static void validateConfiguredKey(MemorySegment databaseHandle, SqliteNativeApi sqliteApi)
      throws SqliteNativeException {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(
          databaseHandle, arena.allocateFrom(KEY_VALIDATION_QUERY), sqliteApi);
    }
  }
}
