package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;

/**
 * Read and preflight capability shared by every ledger-plan execution mode.
 *
 * <p>This deliberately excludes a transaction-begin operation. A caller receives either the
 * aggregate-attested transaction capability or the credential-free read-only transaction
 * capability, never an interchangeable superset.
 */
public interface LedgerPlanReadStore
    extends BookkeepingReadStore, AttestationPostingCommitmentStore, PostingValidationStore {}
