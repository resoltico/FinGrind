package dev.erst.fingrind.executor.spi;

/** Composite read-side port for inspection, queries, and reporting. */
public interface BookkeepingReadStore
    extends BookLifecycleReader,
        AccountLookupStore,
        AccountCatalogStore,
        PostingLookupStore,
        PostingHistoryStore,
        InventoryValuationStore,
        BookkeepingReportStore {}
