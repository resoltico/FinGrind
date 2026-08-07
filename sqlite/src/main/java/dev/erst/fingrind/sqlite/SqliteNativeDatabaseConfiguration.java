package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeIntCalls;
import java.lang.foreign.Arena;
import java.util.Objects;

/** Applies keying and runtime configuration to one opened native SQLite database. */
final class SqliteNativeDatabaseConfiguration {
  private final SqliteNativeDatabase database;

  SqliteNativeDatabaseConfiguration(SqliteNativeDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  void configureBusyTimeout(int timeoutMillis) {
    int resultCode =
        SqliteNativeInvocation.invoke(
            "Failed to open the SQLite native library bridge.",
            () ->
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeIntCalls.AddressIntToIntCall.class,
                        database.sqliteApi().sqlite3BusyTimeout())
                    .invoke(database.handle(), timeoutMillis));
    SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(resultCode, database.sqliteApi());
  }

  void enableExtendedResultCodes() {
    int resultCode =
        SqliteNativeInvocation.invoke(
            "Failed to open the SQLite native library bridge.",
            () ->
                SqliteNativeCallAdapter.adapt(
                        SqliteNativeIntCalls.AddressIntToIntCall.class,
                        database.sqliteApi().sqlite3ExtendedResultCodes())
                    .invoke(database.handle(), 1));
    SqliteNativeKeyConfiguration.requireOpenConfigurationSuccess(resultCode, database.sqliteApi());
  }

  void rekey(SqliteBookPassphrase bookPassphrase) {
    Objects.requireNonNull(bookPassphrase, "bookPassphrase");
    try (Arena arena = Arena.ofConfined();
        SqliteNativeSecretBuffer keyBuffer =
            SqliteNativeSecretBuffer.cString(bookPassphrase, arena)) {
      int resultCode =
          SqliteNativeInvocation.invokeSqlite(
              "Failed to rekey the FinGrind SQLite book with passphrase material from "
                  + bookPassphrase.sourceDescription()
                  + ".",
              () ->
                  SqliteNativeCallAdapter.adapt(
                          SqliteNativeIntCalls.AddressAddressIntToIntCall.class,
                          database.sqliteApi().sqlite3Rekey())
                      .invoke(database.handle(), keyBuffer.pointer(), bookPassphrase.byteLength()));
      if (resultCode != SqliteNativeResultCode.code("OK")) {
        throw SqliteNativeErrors.failure(resultCode, database.sqliteApi());
      }
    }
  }

  void validateConfiguredKey() {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(
          database.handle(),
          arena.allocateFrom(SqliteNativeKeyConfiguration.KEY_VALIDATION_QUERY),
          database.sqliteApi());
    }
  }
}
