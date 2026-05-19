package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.BookkeepingReadStore;

/** Public SQLite-backed read session for lifecycle inspection, queries, and reports. */
public interface SqliteReadSession extends BookkeepingReadStore, AutoCloseable {
  @Override
  void close();
}
