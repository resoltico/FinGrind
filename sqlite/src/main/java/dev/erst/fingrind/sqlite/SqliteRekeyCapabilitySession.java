package dev.erst.fingrind.sqlite;

/** Rekey-only wrapper over the shared SQLite store core. */
public final class SqliteRekeyCapabilitySession extends SqliteDelegatingSession
    implements SqliteRekeyCapabilityView {
  SqliteRekeyCapabilitySession(SqlitePostingFactStore store) {
    super(store);
  }

  @Override
  public SqliteStoreMutationOperations storeMutationOperations() {
    return store.storeMutationOperations();
  }

  @Override
  public java.nio.file.Path storeBookPath() {
    return super.storeBookPath();
  }

  @Override
  public void close() {
    closeStore();
  }
}
