package dev.erst.fingrind.sqlite;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

/** Thread-confined SQLite database handle backed by the configured SQLite C library. */
class SqliteNativeDatabase implements AutoCloseable {
  private final MemorySegment databaseHandle;
  private final SqliteNativeApi sqliteApi;
  private final SqliteThreadOwner threadOwner =
      new SqliteThreadOwner("SQLite native database handle");
  private boolean closed;

  SqliteNativeDatabase(MemorySegment databaseHandle) {
    this(databaseHandle, SqliteNativeBootstrap.api());
  }

  SqliteNativeDatabase(MemorySegment databaseHandle, SqliteNativeApi sqliteApi) {
    this.databaseHandle = Objects.requireNonNull(databaseHandle, "databaseHandle");
    this.sqliteApi = Objects.requireNonNull(sqliteApi, "sqliteApi");
  }

  MemorySegment handle() {
    threadOwner.requireOwnerThread();
    ensureOpen();
    return databaseHandle;
  }

  SqliteNativeApi sqliteApi() {
    threadOwner.requireOwnerThread();
    return sqliteApi;
  }

  SqliteNativeStatement prepare(String sql) {
    threadOwner.requireOwnerThread();
    ensureOpen();
    return SqliteNativeStatements.prepare(this, sql);
  }

  /**
   * Executes one control statement that must not yield result rows.
   *
   * <p>Row-producing SQL uses {@link SqliteNativeStatement} directly instead of this helper.
   */
  void executeStatement(String sql) {
    threadOwner.requireOwnerThread();
    ensureOpen();
    try (SqliteNativeStatement statement = prepare(sql)) {
      int resultCode = statement.step();
      if (resultCode != SqliteNativeResultCodes.DONE) {
        throw new IllegalStateException("SQLite control statement must not produce rows: " + sql);
      }
    }
  }

  /** Executes one multi-statement SQL script through {@code sqlite3_exec}. */
  void executeScript(String sql) {
    threadOwner.requireOwnerThread();
    ensureOpen();
    try (Arena arena = Arena.ofConfined()) {
      SqliteNativeStatements.executeScript(handle(), arena.allocateFrom(sql), sqliteApi);
    }
  }

  @Override
  public void close() {
    threadOwner.requireOwnerThread();
    if (closed) {
      return;
    }
    SqliteNativeConnections.close(databaseHandle, sqliteApi);
    closed = true;
  }

  private void ensureOpen() {
    if (closed) {
      throw new IllegalStateException("SQLite native database handle is already closed.");
    }
  }
}
