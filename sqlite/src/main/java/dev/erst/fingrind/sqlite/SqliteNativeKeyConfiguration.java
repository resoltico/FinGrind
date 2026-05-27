package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import dev.erst.fingrind.sqlite.secret.SqliteBookPassphrase;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;

/** Applies book keys, rekeys databases, and validates configured SQLite encryption state. */
final class SqliteNativeKeyConfiguration {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  private static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

  private SqliteNativeKeyConfiguration() {}

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
      SqliteNativeConnections.suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    } catch (Error error) {
      SqliteNativeConnections.suppressCloseFailure(databaseHandle, sqliteApi, error);
      throw error;
    } catch (RuntimeException exception) {
      SqliteNativeConnections.suppressCloseFailure(databaseHandle, sqliteApi, exception);
      throw exception;
    }
  }

  static void requireOpenConfigurationSuccess(int resultCode, SqliteNativeApi sqliteApi) {
    if (resultCode != SqliteNativeResultCodes.OK) {
      throw SqliteNativeErrors.failure(resultCode, sqliteApi);
    }
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
