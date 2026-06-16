package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;

/** Period-close wrapper over the shared SQLite store core. */
final class SqlitePeriodResultTransferCapabilitySession extends SqliteDelegatingSession
    implements SqlitePeriodResultTransferCapabilityView {
  SqlitePeriodResultTransferCapabilitySession(SqlitePostingFactStore store) {
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

  PeriodResultTransferOutcome transferPeriodResult(
      PeriodResultTransferDraft periodResultTransferDraft, PostingIdGenerator postingIdGenerator) {
    return store.transferPeriodResult(periodResultTransferDraft, postingIdGenerator);
  }

  @Override
  public void close() {
    closeStore();
  }
}
