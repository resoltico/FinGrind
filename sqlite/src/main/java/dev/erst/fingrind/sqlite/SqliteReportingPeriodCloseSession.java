package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingRangeStore;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;

/** Public SQLite-backed session for reporting-period-close workflows. */
public interface SqliteReportingPeriodCloseSession
    extends BookLifecycleReader,
        AccountCatalogStore,
        PostingRangeStore,
        ReportingPeriodCloseStore,
        AutoCloseable {
  @Override
  void close();
}
