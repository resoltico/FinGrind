package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodCloseStore;
import dev.erst.fingrind.executor.spi.PostingRangeStore;

/** Public SQLite-backed session for period-close workflows. */
public interface SqlitePeriodCloseSession
    extends BookLifecycleReader,
        AccountCatalogStore,
        PostingRangeStore,
        PeriodCloseStore,
        AutoCloseable {
  @Override
  void close();
}
