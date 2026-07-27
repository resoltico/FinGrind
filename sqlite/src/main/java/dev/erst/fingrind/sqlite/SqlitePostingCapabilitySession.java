package dev.erst.fingrind.sqlite;

/** Posting-capable wrapper over the shared SQLite store core. */
class SqlitePostingCapabilitySession extends SqliteReadCapabilitySession
    implements SqlitePostingCapabilityView {
  SqlitePostingCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public SqliteStorePostingMutationOperations storePostingMutationOperations() {
    return store.storePostingMutationOperations();
  }

  @Override
  public SqliteStoreAdministrationMutationOperations storeAdministrationMutationOperations() {
    return store.storeAdministrationMutationOperations();
  }

  @Override
  public SqliteStoreAccountRegistryMutationOperations storeAccountRegistryMutationOperations() {
    return store.storeAccountRegistryMutationOperations();
  }
}
