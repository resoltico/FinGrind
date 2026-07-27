package dev.erst.fingrind.executor.spi;

/**
 * One bound protected-book session for every read, child mutation, and final attestation in a
 * ledger-plan execution.
 */
public interface LedgerPlanExecutionStore
    extends LedgerPlanReadStore, LedgerPlanTransaction, LedgerPlanMutationStore {}
