package dev.erst.fingrind.sqlite;

/** Native SQLite open policies that FinGrind maps from its command-level access intents. */
enum SqliteNativeOpenMode {
  READ_ONLY(SqliteNativeLibrary.SQLITE_OPEN_READONLY),
  READ_WRITE_EXISTING(SqliteNativeLibrary.SQLITE_OPEN_READWRITE),
  READ_WRITE_CREATE(
      SqliteNativeLibrary.SQLITE_OPEN_READWRITE | SqliteNativeLibrary.SQLITE_OPEN_CREATE);

  private final int flags;

  SqliteNativeOpenMode(int flags) {
    this.flags = flags;
  }

  int flags() {
    return flags;
  }
}
