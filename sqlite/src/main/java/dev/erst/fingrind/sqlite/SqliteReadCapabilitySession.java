package dev.erst.fingrind.sqlite;

/** Read-only wrapper over the shared SQLite store core. */
class SqliteReadCapabilitySession extends SqliteDelegatingSession
    implements SqliteReadCapabilityView {
  SqliteReadCapabilitySession(SqlitePostingFactStore store) {
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
