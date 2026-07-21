package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;

/** Period-close wrapper over the shared SQLite store core. */
final class SqliteReportingPeriodCloseCapabilitySession extends SqliteDelegatingSession
    implements SqliteReportingPeriodCloseCapabilityView {
  SqliteReportingPeriodCloseCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public SqliteThreadOwner storeThreadOwner() {
    return store.storeThreadOwner();
  }

  @Override
  public SqliteStoreReadOperations storeReadOperations() {
    return store.storeReadOperations();
  }

  @Override
  public SqliteStoreMutationOperations storeMutationOperations() {
    return store.storeMutationOperations();
  }

  @Override
  public SqliteStoreLifecycle storeLifecycle() {
    return store.storeLifecycle();
  }

  @Override
  public SqliteStoreContext storeContext() {
    return store.storeContext();
  }

  InterimResultSweepOutcome interimResultSweep(
      InterimResultSweepDraft interimResultSweepDraft,
      PostingIdGenerator postingIdGenerator,
      AttestationOperationAuthorizer attestationAuthorizer) {
    return store.interimResultSweep(
        interimResultSweepDraft, postingIdGenerator, attestationAuthorizer);
  }

  @Override
  public void close() {
    closeStore();
  }
}
