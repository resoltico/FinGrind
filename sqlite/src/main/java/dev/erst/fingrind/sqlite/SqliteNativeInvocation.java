package dev.erst.fingrind.sqlite;

import org.jspecify.annotations.Nullable;

/** Narrow wrapper around typed SQLite bridge calls that preserve structured native failures. */
final class SqliteNativeInvocation {
  private SqliteNativeInvocation() {}

  /** One native method-handle call that returns a value. */
  @FunctionalInterface
  interface NativeCall<T extends @Nullable Object> {
    /** Executes one native bridge call. */
    T invoke();
  }

  /** One native method-handle call that returns no value. */
  @FunctionalInterface
  interface NativeAction {
    /** Executes one native bridge side effect. */
    void run();
  }

  /** One native bridge call that may report a typed SQLite failure. */
  @FunctionalInterface
  interface NativeSqliteCall<T extends @Nullable Object> {
    /** Executes one native SQLite bridge call. */
    T invoke();
  }

  /** One native bridge side effect that may report a typed SQLite failure. */
  @FunctionalInterface
  interface NativeSqliteAction {
    /** Executes one native SQLite bridge side effect. */
    void run();
  }

  static <T extends @Nullable Object> T invoke(String failureMessage, NativeCall<T> nativeCall) {
    try {
      return nativeCall.invoke();
    } catch (RuntimeException exception) {
      throw new IllegalStateException(failureMessage, exception);
    }
  }

  static void run(String failureMessage, NativeAction nativeAction) {
    invoke(
        failureMessage,
        () -> {
          nativeAction.run();
          return Boolean.TRUE;
        });
  }

  static <T extends @Nullable Object> T invokeSqlite(
      String failureMessage, NativeSqliteCall<T> nativeCall) {
    try {
      return nativeCall.invoke();
    } catch (SqliteNativeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new IllegalStateException(failureMessage, exception);
    }
  }

  static void runSqlite(String failureMessage, NativeSqliteAction nativeAction) {
    invokeSqlite(
        failureMessage,
        () -> {
          nativeAction.run();
          return Boolean.TRUE;
        });
  }
}
