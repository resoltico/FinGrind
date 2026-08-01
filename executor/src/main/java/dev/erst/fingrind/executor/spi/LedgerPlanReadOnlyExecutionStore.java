package dev.erst.fingrind.executor.spi;

/**
 * Capability set for a credential-free ledger plan that may inspect, preflight, query, or assert
 * but cannot mutate the protected book.
 */
public interface LedgerPlanReadOnlyExecutionStore
    extends LedgerPlanReadStore, LedgerPlanReadOnlyTransaction {}
