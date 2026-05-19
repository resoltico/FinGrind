package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;

/** Public SQLite-backed administration session for lifecycle and account-registry workflows. */
public interface SqliteAdministrationSession
    extends BookLifecycleReader, BookAdministrationStore, AccountCatalogStore, AutoCloseable {
  @Override
  void close();
}
