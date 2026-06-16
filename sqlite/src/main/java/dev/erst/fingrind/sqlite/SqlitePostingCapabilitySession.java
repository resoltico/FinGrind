package dev.erst.fingrind.sqlite;

/** Posting-capable wrapper over the shared SQLite store core. */
class SqlitePostingCapabilitySession extends SqliteReadCapabilitySession
    implements SqlitePostingCapabilityView {
  SqlitePostingCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public SqliteStoreMutationOperations storeMutationOperations() {
    return store.storeMutationOperations();
  }
}
