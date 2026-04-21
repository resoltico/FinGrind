package dev.erst.fingrind.sqlite;

/** Whether one mutation path owns the current SQLite transaction boundary. */
enum SqliteTransactionOwnership {
  OWNED,
  SHARED
}
