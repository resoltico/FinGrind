package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.Objects;

/** Applies book keys, rekeys databases, and validates configured SQLite encryption state. */
final class SqliteNativeKeyConfiguration {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

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
      SqliteNativeDatabase configuredDatabase =
          new SqliteNativeDatabase(
              databaseHandle, normalizedBookPath, openMode.publishesActivityMarker(), sqliteApi);
      configuredDatabase.configuration().configureBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
      configuredDatabase.configuration().enableExtendedResultCodes();
      configuredDatabase.configuration().validateConfiguredKey();
      return configuredDatabase;
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
    database.configuration().rekey(bookPassphrase);
    database.configuration().validateConfiguredKey();
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
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeCalls.AddressAddressIntToIntCall.class, sqliteApi.sqlite3Key())
                    .invoke(databaseHandle, keyBuffer.pointer(), bookPassphrase.byteLength());
            requireOpenConfigurationSuccess(resultCode, sqliteApi);
          }
        });
  }
}
