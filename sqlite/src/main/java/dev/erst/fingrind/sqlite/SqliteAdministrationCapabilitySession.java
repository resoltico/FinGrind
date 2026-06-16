package dev.erst.fingrind.sqlite;

/** Administration-only wrapper over the shared SQLite store core. */
final class SqliteAdministrationCapabilitySession extends SqliteDelegatingSession
    implements SqliteAdministrationCapabilityView {
  SqliteAdministrationCapabilitySession(SqlitePostingFactStore store) {
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

  @Override
  public void close() {
    closeStore();
  }
}
