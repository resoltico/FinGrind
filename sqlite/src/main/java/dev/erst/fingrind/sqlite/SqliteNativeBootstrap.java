package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.function.Supplier;

/** Process-scoped bootstrap and publication of the SQLite native API bundle. */
final class SqliteNativeBootstrap {
  private static final java.lang.foreign.Linker LINKER = java.lang.foreign.Linker.nativeLinker();
  private static final String STRLEN_SYMBOL = "strlen";
  private static final java.lang.foreign.FunctionDescriptor STRLEN_DESCRIPTOR =
      java.lang.foreign.FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);

  private SqliteNativeBootstrap() {}

  static SqliteNativeApi api() {
    return initialize(() -> SqliteApiHolder.INSTANCE);
  }

  static MethodHandle strlen() {
    return initialize(() -> StrlenHolder.INSTANCE);
  }

  static <T> T initialize(Supplier<T> initializer) {
    Objects.requireNonNull(initializer, "initializer");
    try {
      return initializer.get();
    } catch (ExceptionInInitializerError error) {
      throw nativeInitializationFailure(error);
    }
  }

  static IllegalStateException nativeInitializationFailure(ExceptionInInitializerError error) {
    Objects.requireNonNull(error, "error");
    Throwable cause = error.getCause();
    if (cause instanceof IllegalStateException illegalStateException) {
      return illegalStateException;
    }
    Throwable reportedCause = cause == null ? error : cause;
    return new IllegalStateException(
        Objects.requireNonNullElse(
            reportedCause.getMessage(), "Failed to initialize SQLite native library."),
        reportedCause);
  }

  static void shutdownIfQuiescent(MethodHandle sqlite3Shutdown, int activeConnections) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    if (activeConnections == 0) {
      shutdownQuietly(sqlite3Shutdown);
    }
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown) {
    shutdownQuietly(sqlite3Shutdown, SqliteBestEffort::reportCleanupFailure);
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown, SqliteBestEffort.Reporter reporter) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    Objects.requireNonNull(reporter, "reporter");
    try {
      SqliteNativeCalls.noArgInt(sqlite3Shutdown).invoke();
    } catch (RuntimeException exception) {
      reporter.report("shutting down the process-scoped SQLite runtime", exception);
    }
  }

  /** Lazily publishes the process-scoped SQLite native API bundle on first use. */
  private static final class SqliteApiHolder {
    private static final SqliteNativeApi INSTANCE = SqliteNativeApiLoader.loadApi();

    private SqliteApiHolder() {}
  }

  /** Lazily resolves the process-global C runtime {@code strlen} symbol on first use. */
  private static final class StrlenHolder {
    private static final MethodHandle INSTANCE =
        SqliteNativeApiLoader.downcall(LINKER.defaultLookup(), STRLEN_SYMBOL, STRLEN_DESCRIPTOR);

    private StrlenHolder() {}
  }
}
