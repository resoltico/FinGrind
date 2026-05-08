package dev.erst.fingrind.sqlite;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.jspecify.annotations.Nullable;

/** Shared test-fixture access seam for store lifecycle inspection and injection. */
public final class SqliteStoreTestAccess {
  private static final MethodHandle CLEANUP_CREATED_MISSING_BOOK_ARTIFACTS_IF_PRESENT =
      lifecycleHelper("cleanupCreatedMissingBookArtifactsIfPresent");

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

  /**
   * Invokes missing-book artifact cleanup from the lifecycle seam for explicit state-machine tests.
   */
  public static void invokeCleanupCreatedMissingBookArtifactsIfPresent(
      SqlitePostingFactStore store) {
    try {
      CLEANUP_CREATED_MISSING_BOOK_ARTIFACTS_IF_PRESENT.invoke(store.lifecycle);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new LinkageError("Failed to invoke lifecycle artifact cleanup helper.", throwable);
    }
  }

  private static MethodHandle lifecycleHelper(String methodName) {
    try {
      MethodHandles.Lookup lifecycleLookup =
          MethodHandles.privateLookupIn(SqliteStoreLifecycle.class, MethodHandles.lookup());
      return lifecycleLookup.findVirtual(
          SqliteStoreLifecycle.class, methodName, MethodType.methodType(void.class));
    } catch (IllegalAccessException | NoSuchMethodException exception) {
      throw new LinkageError("Failed to bind SQLite lifecycle helper: " + methodName, exception);
    }
  }
}
