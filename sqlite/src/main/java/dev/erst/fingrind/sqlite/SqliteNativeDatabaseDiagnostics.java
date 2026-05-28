package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Reads one database handle's diagnostic state through the native SQLite bridge. */
final class SqliteNativeDatabaseDiagnostics {
  private final SqliteNativeDatabase database;

  SqliteNativeDatabaseDiagnostics(SqliteNativeDatabase database) {
    this.database = Objects.requireNonNull(database, "database");
  }

  int extendedErrorCode() {
    return SqliteNativeStatements.extendedErrorCode(database.handle(), database.sqliteApi());
  }

  String errorMessage() {
    return SqliteNativeErrors.errorMessage(database.handle(), database.sqliteApi().sqlite3Errmsg());
  }
}
