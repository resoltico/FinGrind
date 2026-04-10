package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Open in-process SQLite database handle backed by the configured SQLite C library. */
final class SqliteNativeDatabase {
  private final MemorySegment databaseHandle;

  private boolean closed;

  SqliteNativeDatabase(MemorySegment databaseHandle) {
    this.databaseHandle = Objects.requireNonNull(databaseHandle, "databaseHandle");
  }

  MemorySegment handle() {
    return databaseHandle;
  }

  /**
   * Executes one control statement that must not yield result rows.
   *
   * <p>Row-producing SQL uses {@link SqliteNativeStatement} directly instead of this helper.
   */
  void executeStatement(String sql) throws SqliteNativeException {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(this, sql)) {
      int resultCode = statement.step();
      if (resultCode != SqliteNativeLibrary.SQLITE_DONE) {
        throw new IllegalStateException("SQLite control statement must not produce rows: " + sql);
      }
    }
  }

  /** Executes one multi-statement SQL script through {@code sqlite3_exec}. */
  void executeScript(String sql) throws SqliteNativeException {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeLibrary.executeScript(databaseHandle, arena.allocateFrom(sql));
    }
  }

  void close() throws SqliteNativeException {
    if (closed) {
      return;
    }
    SqliteNativeLibrary.close(databaseHandle);
    closed = true;
  }
}
