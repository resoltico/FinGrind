package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingRangeStore;

/** Public SQLite-backed session for period-result-transfer workflows. */
public interface SqlitePeriodResultTransferSession
    extends BookLifecycleReader,
        AccountCatalogStore,
        PostingRangeStore,
        PeriodResultTransferStore,
        AutoCloseable {
  @Override
  void close();
}
