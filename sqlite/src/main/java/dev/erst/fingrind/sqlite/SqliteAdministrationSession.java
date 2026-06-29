package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.spi.AccountCatalogStore;
import dev.erst.fingrind.executor.spi.AccountLookupStore;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.TaxAdministrationStore;
import dev.erst.fingrind.executor.spi.TaxRegistrationCatalogStore;

/** Public SQLite-backed administration session for lifecycle and account-registry workflows. */
public interface SqliteAdministrationSession
    extends BookLifecycleReader,
        BookAdministrationStore,
        TaxAdministrationStore,
        AccountCatalogStore,
        AccountLookupStore,
        TaxRegistrationCatalogStore,
        AutoCloseable {
  @Override
  void close();
}
