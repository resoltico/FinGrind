package dev.erst.fingrind.executor.spi;

/** Composite read-side port for tax registration queries and obligation reporting. */
public interface TaxReadStore
    extends BookLifecycleReader, TaxRegistrationCatalogStore, PostingRangeStore {}
