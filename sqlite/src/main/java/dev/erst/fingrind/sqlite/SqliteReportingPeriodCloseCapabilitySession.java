package dev.erst.fingrind.sqlite;

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
  public SqliteClosingMutationOperations storeClosingMutationOperations() {
    return store.storeClosingMutationOperations();
  }

  @Override
  public SqliteStoreLifecycle storeLifecycle() {
    return store.storeLifecycle();
  }

  @Override
  public SqliteStoreContext storeContext() {
    return store.storeContext();
  }

  @Override
  public void close() {
    closeStore();
  }
}
