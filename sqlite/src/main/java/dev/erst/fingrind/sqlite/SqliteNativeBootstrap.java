package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Process-scoped bootstrap and publication of the SQLite native API bundle. */
final class SqliteNativeBootstrap {
  private static final java.lang.foreign.Linker LINKER = java.lang.foreign.Linker.nativeLinker();
  private static final java.lang.foreign.SymbolLookup DEFAULT_LOOKUP = LINKER.defaultLookup();
  private static final String STRLEN_SYMBOL = "strlen";
  private static final java.lang.foreign.FunctionDescriptor STRLEN_DESCRIPTOR =
      java.lang.foreign.FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
  private static final MethodHandle STRLEN =
      SqliteNativeApiLoader.downcall(DEFAULT_LOOKUP, STRLEN_SYMBOL, STRLEN_DESCRIPTOR);
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();

  private SqliteNativeBootstrap() {}

  static SqliteNativeApi api() {
    return initialize(() -> SqliteApiHolder.INSTANCE);
  }

  static MethodHandle strlen() {
    return STRLEN;
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

  static String sqliteVersion() {
    return api().loadedVersion();
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle) {
    return sqliteVersion(libraryVersionHandle, STRLEN);
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite library version.",
        () -> {
          MemorySegment versionPointer =
              SqliteNativeCalls.noArgAddress(libraryVersionHandle).invoke();
          return SqliteNativeErrors.cString(versionPointer, strlenHandle);
        });
  }

  static String sqlite3MultipleCiphersVersion() {
    return api().loadedSqlite3mcVersion();
  }

  static String sqlite3MultipleCiphersVersion(MethodHandle versionHandle) {
    return sqlite3MultipleCiphersVersion(versionHandle, STRLEN);
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite3 Multiple Ciphers library version.",
        () -> {
          MemorySegment versionPointer = SqliteNativeCalls.noArgAddress(versionHandle).invoke();
          String loadedVersion = SqliteNativeErrors.cString(versionPointer, strlenHandle);
          return loadedVersion.replace("SQLite3 Multiple Ciphers ", "").trim();
        });
  }

  static void recordOpenedConnection() {
    ACTIVE_CONNECTIONS.incrementAndGet();
  }

  static void recordClosedConnection() {
    ACTIVE_CONNECTIONS.decrementAndGet();
  }

  static int activeConnectionCount() {
    return ACTIVE_CONNECTIONS.get();
  }

  static void shutdownIfQuiescent(MethodHandle sqlite3Shutdown, int activeConnections) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    if (activeConnections == 0) {
      shutdownQuietly(sqlite3Shutdown);
    }
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    try {
      SqliteNativeCalls.noArgInt(sqlite3Shutdown).invoke();
    } catch (RuntimeException exception) {
      SqliteBestEffort.reportCleanupFailure(
          "shutting down the process-scoped SQLite runtime", exception);
    }
  }

  /** Lazily publishes the process-scoped SQLite native API bundle on first use. */
  private static final class SqliteApiHolder {
    private static final SqliteNativeApi INSTANCE = SqliteNativeApiLoader.loadApi();

    private SqliteApiHolder() {}
  }
}
