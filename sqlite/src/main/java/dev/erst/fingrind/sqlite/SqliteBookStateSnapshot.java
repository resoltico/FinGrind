package dev.erst.fingrind.sqlite;

/** One cached snapshot of the selected SQLite book header and interpreted lifecycle state. */
record SqliteBookStateSnapshot(int applicationId, int userVersion, SqliteBookState state) {
  SqliteBookStateSnapshot {
    if (applicationId < 0) {
      throw new IllegalArgumentException("SQLite applicationId must be non-negative.");
    }
    if (userVersion < 0) {
      throw new IllegalArgumentException("SQLite userVersion must be non-negative.");
    }
  }
}
