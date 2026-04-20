package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Session-owned SQLite handle that is closed only by the surrounding store lifecycle. */
final class SqliteSessionDatabase {
  private final SqliteNativeDatabase nativeDatabase;

  SqliteSessionDatabase(SqliteNativeDatabase nativeDatabase) {
    this.nativeDatabase = Objects.requireNonNull(nativeDatabase, "nativeDatabase");
  }

  SqliteNativeDatabase nativeDatabase() {
    return nativeDatabase;
  }

  void executeStatement(String sql) throws SqliteNativeException {
    nativeDatabase.executeStatement(sql);
  }
}
