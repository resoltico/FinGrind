package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.ContractFailureException;
import dev.erst.fingrind.executor.bookkeeping.PostingAcceptancePolicy;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Shared lifecycle reflection helpers for split SQLite store lifecycle coverage tests. */
abstract class SqliteStoreLifecycleTestSupport extends SqlitePostingFactStoreTestSupport {
  protected static final PostingAcceptancePolicy POSTING_ACCEPTANCE_POLICY =
      PostingAcceptancePolicy.currentKernel();
  private static final Class<?> SESSION_STATE_CLASS = SqliteStoreSessionState.class;
  private static final MethodHandles.Lookup LIFECYCLE_LOOKUP = lifecycleLookup();
  private static final VarHandle SESSION_STATE_HANDLE = lifecycleSessionStateHandle();
  private static final MethodHandle CACHED_BOOK_STATE_HANDLE =
      lifecycleMethodHandle(
          "cachedBookState", MethodType.methodType(SqliteBookStateSnapshot.class));
  private static final MethodHandle DETACH_PUBLISHED_DATABASE_HANDLE =
      lifecycleMethodHandle(
          "detachPublishedDatabase", MethodType.methodType(SqliteNativeDatabase.class));
  private static final MethodHandle REMEMBER_TERMINAL_FAILURE_HANDLE =
      lifecycleMethodHandle(
          "rememberTerminalFailure",
          MethodType.methodType(IllegalStateException.class, IllegalStateException.class));
  private static final MethodHandle REMEMBERED_REJECTED_FAILURE_HANDLE =
      lifecycleMethodHandle(
          "rememberedRejectedFailure",
          MethodType.methodType(
              ContractFailureException.class,
              dev.erst.fingrind.contract.runtime.ContractFailure.class));

  protected void assertProtectedBookVerificationFailure(Path bookPath) {
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalStateException.class, postingFactStore::inspectBook);
      assertProtectedBookVerificationFailure(exception);
    }
  }

  protected static void exerciseIdleStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteBookStateSnapshot snapshot) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("IdleSession", snapshot));
    lifecycle.clearCachedState();
    org.junit.jupiter.api.Assertions.assertEquals(null, invokeCachedBookState(lifecycle));
    lifecycle.clearDatabaseState();
    org.junit.jupiter.api.Assertions.assertEquals(
        null, postingFactStore.lifecycle.publishedDatabase());
    org.junit.jupiter.api.Assertions.assertEquals(null, invokeDetachPublishedDatabase(lifecycle));
  }

  protected static void exerciseOpenedStateBranches(
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("OpenedSession", database, snapshot));
    lifecycle.publishDatabase(database);
    IllegalStateException rememberedOpenedFailure = new IllegalStateException("opened-failure");
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedOpenedFailure, invokeRememberTerminalFailure(lifecycle, rememberedOpenedFailure));
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedOpenedFailure,
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  protected static void exerciseFailedStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot,
      SqliteBookStateSnapshot replacementSnapshot,
      IllegalStateException failedState) {
    setLifecycleSessionState(
        lifecycle, lifecycleSessionState("FailedSession", database, snapshot, failedState));
    lifecycle.cacheState(replacementSnapshot);
    org.junit.jupiter.api.Assertions.assertEquals(
        replacementSnapshot, invokeCachedBookState(lifecycle));
    lifecycle.clearCachedState();
    org.junit.jupiter.api.Assertions.assertEquals(null, invokeCachedBookState(lifecycle));
    lifecycle.publishDatabase(database);
    org.junit.jupiter.api.Assertions.assertSame(
        database, postingFactStore.lifecycle.publishedDatabase());
    org.junit.jupiter.api.Assertions.assertSame(database, invokeDetachPublishedDatabase(lifecycle));
    lifecycle.clearDatabaseState();
    org.junit.jupiter.api.Assertions.assertEquals(
        null, postingFactStore.lifecycle.publishedDatabase());
    org.junit.jupiter.api.Assertions.assertSame(
        failedState,
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession));
    IllegalStateException rememberedFailedFailure =
        new IllegalStateException("failed-state-replaced");
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedFailedFailure, invokeRememberTerminalFailure(lifecycle, rememberedFailedFailure));
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedFailedFailure,
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  protected static void exerciseClosedStateBranches(
      SqlitePostingFactStore postingFactStore,
      SqliteStoreLifecycle lifecycle,
      SqliteNativeDatabase database,
      SqliteBookStateSnapshot snapshot,
      IllegalStateException closedFailure) {
    setLifecycleSessionState(lifecycle, lifecycleSessionState("ClosedSession", (Object) null));
    lifecycle.cacheState(snapshot);
    lifecycle.clearCachedState();
    lifecycle.clearDatabaseState();
    lifecycle.publishDatabase(database);
    org.junit.jupiter.api.Assertions.assertEquals(
        null, postingFactStore.lifecycle.publishedDatabase());
    org.junit.jupiter.api.Assertions.assertEquals(null, invokeCachedBookState(lifecycle));
    org.junit.jupiter.api.Assertions.assertEquals(null, invokeDetachPublishedDatabase(lifecycle));
    IllegalStateException closedWithoutFailure =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession);
    org.junit.jupiter.api.Assertions.assertEquals(
        "SQLite book session is already closed.", closedWithoutFailure.getMessage());

    setLifecycleSessionState(lifecycle, lifecycleSessionState("ClosedSession", closedFailure));
    org.junit.jupiter.api.Assertions.assertSame(
        closedFailure,
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession));
    IllegalStateException rememberedClosedFailure = new IllegalStateException("closed-replaced");
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedClosedFailure, invokeRememberTerminalFailure(lifecycle, rememberedClosedFailure));
    org.junit.jupiter.api.Assertions.assertSame(
        rememberedClosedFailure,
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalStateException.class, lifecycle::ensureOpenSession));
  }

  protected static void exerciseRejectedFailureFallbackBranches(SqliteStoreLifecycle lifecycle) {
    ContractFailureException storedContractFailure =
        new ContractFailureException(
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected.", null, null));
    setLifecycleSessionState(
        lifecycle, lifecycleSessionState("FailedSession", null, null, storedContractFailure));
    org.junit.jupiter.api.Assertions.assertSame(
        storedContractFailure,
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected.", null, null)));
    setLifecycleSessionState(
        lifecycle,
        lifecycleSessionState(
            "FailedSession", null, null, new IllegalStateException("plain-failed")));
    ContractFailureException failedFallback =
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected plain failed.", null, null));
    org.junit.jupiter.api.Assertions.assertEquals(
        "Rejected plain failed.", failedFallback.failure().message());

    setLifecycleSessionState(lifecycle, lifecycleSessionState("IdleSession", (Object) null));
    ContractFailureException idleFallback =
        invokeRememberedRejectedFailure(
            lifecycle,
            dev.erst.fingrind.contract.runtime.ContractErrors.Descriptor
                .PROTECTED_BOOK_VERIFICATION_FAILED
                .failure("Rejected fallback.", null, null));
    org.junit.jupiter.api.Assertions.assertEquals(
        "Rejected fallback.", idleFallback.failure().message());
  }

  protected static Object lifecycleSessionState(String simpleName, @Nullable Object... arguments) {
    return switch (simpleName) {
      case "IdleSession" -> new SqliteIdleStoreSession((SqliteBookStateSnapshot) arguments[0]);
      case "OpenedSession" ->
          new SqliteOpenedStoreSession(
              Objects.requireNonNull((SqliteNativeDatabase) arguments[0], "opened database"),
              (SqliteBookStateSnapshot) arguments[1]);
      case "FailedSession" ->
          new SqliteFailedStoreSession(
              (SqliteNativeDatabase) arguments[0],
              (SqliteBookStateSnapshot) arguments[1],
              Objects.requireNonNull(
                  (IllegalStateException) arguments[2], "failed session failure"));
      case "ClosedSession" -> new SqliteClosedStoreSession((IllegalStateException) arguments[0]);
      default -> throw new IllegalArgumentException("Unknown lifecycle state type: " + simpleName);
    };
  }

  protected static void setLifecycleSessionState(
      SqliteStoreLifecycle lifecycle, Object sessionState) {
    SESSION_STATE_HANDLE.set(lifecycle, sessionState);
  }

  protected static @Nullable SqliteBookStateSnapshot invokeCachedBookState(
      SqliteStoreLifecycle lifecycle) {
    return (@Nullable SqliteBookStateSnapshot) invokeHandle(CACHED_BOOK_STATE_HANDLE, lifecycle);
  }

  protected static @Nullable SqliteNativeDatabase invokeDetachPublishedDatabase(
      SqliteStoreLifecycle lifecycle) {
    return (@Nullable SqliteNativeDatabase)
        invokeHandle(DETACH_PUBLISHED_DATABASE_HANDLE, lifecycle);
  }

  protected static IllegalStateException invokeRememberTerminalFailure(
      SqliteStoreLifecycle lifecycle, IllegalStateException failure) {
    return (IllegalStateException)
        invokeHandle(REMEMBER_TERMINAL_FAILURE_HANDLE, lifecycle, failure);
  }

  protected static ContractFailureException invokeRememberedRejectedFailure(
      SqliteStoreLifecycle lifecycle, dev.erst.fingrind.contract.runtime.ContractFailure failure) {
    return (ContractFailureException)
        invokeHandle(REMEMBERED_REJECTED_FAILURE_HANDLE, lifecycle, failure);
  }

  private static Object invokeHandle(MethodHandle handle, @Nullable Object... arguments) {
    try {
      return handle.invokeWithArguments(arguments);
    } catch (RuntimeException runtimeException) {
      throw runtimeException;
    } catch (Error error) {
      throw error;
    } catch (Throwable throwable) {
      throw new AssertionError("Unexpected checked throwable from lifecycle handle.", throwable);
    }
  }

  private static MethodHandle lifecycleMethodHandle(String methodName, MethodType type) {
    try {
      return LIFECYCLE_LOOKUP.findVirtual(SqliteStoreLifecycle.class, methodName, type);
    } catch (NoSuchMethodException | IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static VarHandle lifecycleSessionStateHandle() {
    try {
      MethodHandles.Lookup stateSupportLookup =
          MethodHandles.privateLookupIn(
              SqliteStoreSessionStateTracker.class, MethodHandles.lookup());
      return stateSupportLookup.findVarHandle(
          SqliteStoreSessionStateTracker.class, "sessionState", SESSION_STATE_CLASS);
    } catch (NoSuchFieldException | IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  private static MethodHandles.Lookup lifecycleLookup() {
    try {
      return MethodHandles.privateLookupIn(SqliteStoreLifecycle.class, MethodHandles.lookup());
    } catch (IllegalAccessException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
