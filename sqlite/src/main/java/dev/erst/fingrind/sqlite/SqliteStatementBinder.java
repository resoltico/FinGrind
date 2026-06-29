package dev.erst.fingrind.sqlite;

/** Binds parameters onto a prepared SQLite statement before execution. */
@FunctionalInterface
interface SqliteStatementBinder {
  /** Applies all statement bindings required by one query. */
  void bind(SqliteNativeStatement statement);
}
