package dev.erst.fingrind.sqlite;

import org.jspecify.annotations.Nullable;

/** Same-package test access shim for store lifecycle seams. */
final class SqliteStoreTestAccess {
  private SqliteStoreTestAccess() {}

  static void publishNativeDatabase(
      SqlitePostingFactStore store, @Nullable SqliteNativeDatabase database) {
    store.context().lifecycle().replaceDatabase(database);
  }

  static void setPendingPassphrase(
      SqlitePostingFactStore store, @Nullable SqliteBookPassphrase passphrase) {
    store.context().lifecycle().replacePendingPassphrase(passphrase);
  }

  static SqliteBookPassphrase takePendingPassphrase(SqlitePostingFactStore store) {
    return store.context().lifecycle().takePendingPassphrase();
  }

  static void setCachedState(
      SqlitePostingFactStore store, @Nullable SqliteBookStateSnapshot cachedBookState) {
    store.context().lifecycle().replaceCachedState(cachedBookState);
  }

  static boolean closed(SqlitePostingFactStore store) {
    return store.context().lifecycle().closed();
  }

  static boolean ledgerPlanTransactionActive(SqlitePostingFactStore store) {
    return store.context().lifecycle().ledgerPlanTransactionActive();
  }

  static boolean ledgerPlanTransactionBegunInDatabase(SqlitePostingFactStore store) {
    return store.context().lifecycle().ledgerPlanTransactionBegunInDatabase();
  }

  static @Nullable SqliteNativeDatabase currentDatabaseHandle(SqlitePostingFactStore store) {
    return store.context().lifecycle().currentDatabaseHandle();
  }
}
