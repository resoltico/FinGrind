package dev.erst.fingrind.sqlite;

import org.jspecify.annotations.Nullable;

/** Same-package test access shim for store lifecycle seams. */
final class SqliteStoreTestAccess {
  private SqliteStoreTestAccess() {}

  static void publishNativeDatabase(
      SqlitePostingFactStore store, @Nullable SqliteNativeDatabase database) {
    store.lifecycle().replaceDatabase(database);
  }

  static void setPendingPassphrase(
      SqlitePostingFactStore store, @Nullable SqliteBookPassphrase passphrase) {
    store.lifecycle().replacePendingPassphrase(passphrase);
  }

  static SqliteBookPassphrase takePendingPassphrase(SqlitePostingFactStore store) {
    return store.lifecycle().takePendingPassphrase();
  }

  static void setCachedState(
      SqlitePostingFactStore store, @Nullable SqliteBookStateSnapshot cachedBookState) {
    store.lifecycle().replaceCachedState(cachedBookState);
  }

  static boolean closed(SqlitePostingFactStore store) {
    return store.lifecycle().closed();
  }

  static boolean ledgerPlanTransactionActive(SqlitePostingFactStore store) {
    return store.lifecycle().ledgerPlanTransactionActive();
  }

  static boolean ledgerPlanTransactionBegunInDatabase(SqlitePostingFactStore store) {
    return store.lifecycle().ledgerPlanTransactionBegunInDatabase();
  }

  static @Nullable SqliteNativeDatabase currentDatabaseHandle(SqlitePostingFactStore store) {
    return store.lifecycle().currentDatabaseHandle();
  }
}
