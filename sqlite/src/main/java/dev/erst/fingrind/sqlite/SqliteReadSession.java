package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.TaxReadStore;

/** Public SQLite-backed read session for lifecycle inspection, queries, and reports. */
public interface SqliteReadSession extends BookkeepingReadStore, TaxReadStore, AutoCloseable {
  @Override
  void close();
}
