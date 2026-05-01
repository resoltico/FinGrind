package dev.erst.fingrind.sqlite;

import org.jspecify.annotations.Nullable;

/** Shared test-fixture access seam for store lifecycle inspection and injection. */
public final class SqliteStoreTestAccess {
  private SqliteStoreTestAccess() {}

  /** Replaces the active native database handle held by the store lifecycle for test setup. */
  public static void publishNativeDatabase(
      SqlitePostingFactStore store, @Nullable SqliteNativeDatabase database) {
    if (database == null) {
      store.lifecycle.clearDatabaseState();
      return;
    }
    store.lifecycle.publishDatabase(database);
  }

  /** Clears the session secret so tests can assert the consumed-secret state explicitly. */
  public static void clearSessionSecret(SqlitePostingFactStore store) {
    store.sessionSecret.close();
  }

  /** Replaces the cached book state snapshot that subsequent read paths observe during tests. */
  public static void setCachedState(
      SqlitePostingFactStore store, @Nullable SqliteBookStateSnapshot cachedBookState) {
    if (cachedBookState == null) {
      store.lifecycle.clearCachedState();
      return;
    }
    store.lifecycle.cacheState(cachedBookState);
  }

  /** Clears the currently published database and cached book state so tests can force a reopen. */
  public static void clearPublishedDatabaseState(SqlitePostingFactStore store) {
    store.lifecycle.clearDatabaseState();
  }

  /** Returns whether the lifecycle has entered its terminal closed state. */
  public static boolean closed(SqlitePostingFactStore store) {
    return store.lifecycle.closed();
  }

  /** Returns whether a ledger-plan transaction is currently marked active in lifecycle state. */
  public static boolean ledgerPlanTransactionActive(SqlitePostingFactStore store) {
    return store.lifecycle.ledgerPlanTransactionActive();
  }

  /** Returns whether the active database has begun the current ledger-plan transaction. */
  public static boolean ledgerPlanTransactionBegunInDatabase(SqlitePostingFactStore store) {
    return store.lifecycle.ledgerPlanTransactionBegunInDatabase();
  }

  /** Returns the current published native database handle for lifecycle assertions in tests. */
  public static @Nullable SqliteNativeDatabase publishedDatabase(SqlitePostingFactStore store) {
    return store.lifecycle.publishedDatabase();
  }

  /** Returns the store access mode selected for this SQLite-backed session. */
  public static SqliteStoreAccessMode accessMode(SqlitePostingFactStore store) {
    return store.accessMode();
  }
}
