package dev.erst.fingrind.sqlite;

/** Ledger-plan wrapper that exposes only reads, plan children, and one aggregate append. */
final class SqlitePlanExecutionCapabilitySession extends SqliteReadCapabilitySession
    implements SqlitePlanExecutionCapabilityView {
  SqlitePlanExecutionCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public SqliteStoreAccountRegistryMutationOperations storeAccountRegistryMutationOperations() {
    return store.storeAccountRegistryMutationOperations();
  }

  @Override
  public SqliteStoreAdministrationMutationOperations storeAdministrationMutationOperations() {
    return store.storeAdministrationMutationOperations();
  }

  @Override
  public SqliteStorePostingMutationOperations storePostingMutationOperations() {
    return store.storePostingMutationOperations();
  }
}
