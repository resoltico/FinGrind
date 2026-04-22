package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Open in-process SQLite database handle backed by the configured SQLite C library. */
class SqliteNativeDatabase implements AutoCloseable {
  private final MemorySegment databaseHandle;

  private boolean closed;

  SqliteNativeDatabase(MemorySegment databaseHandle) {
    this.databaseHandle = Objects.requireNonNull(databaseHandle, "databaseHandle");
  }

  MemorySegment handle() {
    return databaseHandle;
  }

  SqliteNativeStatement prepare(String sql) {
    return SqliteNativeStatements.prepare(this, sql);
  }

  /**
   * Executes one control statement that must not yield result rows.
   *
   * <p>Row-producing SQL uses {@link SqliteNativeStatement} directly instead of this helper.
   */
  void executeStatement(String sql) {
    try (SqliteNativeStatement statement = prepare(sql)) {
      int resultCode = statement.step();
      if (resultCode != SqliteNativeResultCodes.DONE) {
        throw new IllegalStateException("SQLite control statement must not produce rows: " + sql);
      }
    }
  }

  /** Executes one multi-statement SQL script through {@code sqlite3_exec}. */
  void executeScript(String sql) {
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(databaseHandle, arena.allocateFrom(sql));
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }
    SqliteNativeConnections.close(databaseHandle);
    closed = true;
  }
}
