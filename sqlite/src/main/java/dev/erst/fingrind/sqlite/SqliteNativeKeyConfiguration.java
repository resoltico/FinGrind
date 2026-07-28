package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Applies book keys, rekeys databases, and validates configured SQLite encryption state. */
final class SqliteNativeKeyConfiguration {
  private static final int SQLITE_BUSY_TIMEOUT_MILLIS = 5_000;
  static final String KEY_VALIDATION_QUERY = "SELECT count(*) FROM sqlite_master;";

  private SqliteNativeKeyConfiguration() {}

  static SqliteNativeDatabase configureOpenedDatabase(
      MemorySegment databaseHandle,
      SqliteBookPassphrase bookPassphrase,
      @Nullable SqliteNativeActivityRegistration activityRegistration,
      SqliteNativeApi sqliteApi,
      Arena arena) {
    try {
      applyKey(databaseHandle, bookPassphrase, sqliteApi, arena);
      SqliteNativeDatabase configuredDatabase =
          new SqliteNativeDatabase(databaseHandle, activityRegistration, sqliteApi);
      configuredDatabase.configuration().configureBusyTimeout(SQLITE_BUSY_TIMEOUT_MILLIS);
      configuredDatabase.configuration().enableExtendedResultCodes();
      configuredDatabase.configuration().validateConfiguredKey();
      return configuredDatabase;
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
    if (resultCode != SqliteNativeResultCode.code("OK")) {
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

  static void suppressCloseFailure(
      MemorySegment databaseHandle, SqliteNativeApi sqliteApi, Throwable primaryFailure) {
    if (databaseHandle.equals(MemorySegment.NULL)) {
      return;
    }
    try {
      int resultCode =
          SqliteNativeCallAdapter.adapt(
                  SqliteNativeCalls.AddressToIntCall.class, sqliteApi.sqlite3CloseV2())
              .invoke(databaseHandle);
      if (resultCode != SqliteNativeResultCode.code("OK")) {
        primaryFailure.addSuppressed(SqliteNativeErrors.failure(resultCode, sqliteApi));
      }
    } catch (RuntimeException exception) {
      primaryFailure.addSuppressed(exception);
    }
  }
}
