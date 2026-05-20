package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCalls;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Process-scoped bootstrap and publication of the SQLite native API bundle. */
final class SqliteNativeBootstrap {
  private static final java.lang.foreign.Linker LINKER = java.lang.foreign.Linker.nativeLinker();
  private static final String STRLEN_SYMBOL = "strlen";
  private static final java.lang.foreign.FunctionDescriptor STRLEN_DESCRIPTOR =
      java.lang.foreign.FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
  private static final AtomicInteger ACTIVE_CONNECTIONS = new AtomicInteger();
  private static final ConcurrentMap<Path, AtomicInteger> ACTIVE_CONNECTIONS_BY_BOOK_PATH =
      new ConcurrentHashMap<>();
  private static final ConcurrentMap<Path, AtomicInteger> MARKER_CONNECTIONS_BY_BOOK_PATH =
      new ConcurrentHashMap<>();

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

  static String sqliteVersion() {
    return api().loadedVersion();
  }

  static String sqliteVersion(MethodHandle libraryVersionHandle) {
    return sqliteVersion(libraryVersionHandle, strlen());
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

  static String sqliteSourceId() {
    return api().loadedSourceId();
  }

  static String sqlite3MultipleCiphersVersion(MethodHandle versionHandle) {
    return sqlite3MultipleCiphersVersion(versionHandle, strlen());
  }

  static String sqlite3MultipleCiphersVersion(
      MethodHandle versionHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite3 Multiple Ciphers library version.",
        () -> {
          MemorySegment versionPointer = SqliteNativeCalls.noArgAddress(versionHandle).invoke();
          String loadedVersion = SqliteNativeErrors.cString(versionPointer, strlenHandle);
          return loadedVersion.replace("SQLite3 Multiple Ciphers ", "").strip();
        });
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle) {
    return sqliteSourceId(sourceIdHandle, strlen());
  }

  static String sqliteSourceId(MethodHandle sourceIdHandle, MethodHandle strlenHandle) {
    return SqliteNativeInvocation.invoke(
        "Failed to read the SQLite source id.",
        () -> {
          MemorySegment sourceIdPointer = SqliteNativeCalls.noArgAddress(sourceIdHandle).invoke();
          return SqliteNativeErrors.cString(sourceIdPointer, strlenHandle);
        });
  }

  static void recordOpeningConnection(Path normalizedBookPath) {
    recordOpeningConnection(normalizedBookPath, true);
  }

  static void recordOpeningConnection(Path normalizedBookPath, boolean publishesActivityMarker) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    ACTIVE_CONNECTIONS.incrementAndGet();
    AtomicInteger activeBookConnections =
        ACTIVE_CONNECTIONS_BY_BOOK_PATH.computeIfAbsent(
            normalizedBookPath, ignored -> new AtomicInteger());
    activeBookConnections.incrementAndGet();
    if (!publishesActivityMarker) {
      return;
    }
    AtomicInteger markerConnections =
        MARKER_CONNECTIONS_BY_BOOK_PATH.computeIfAbsent(
            normalizedBookPath, ignored -> new AtomicInteger());
    int openedMarkerConnections = markerConnections.incrementAndGet();
    if (openedMarkerConnections == 1) {
      try {
        SqliteBookActivityMarkers.createCurrentProcessMarker(normalizedBookPath);
      } catch (RuntimeException exception) {
        rollbackOpeningConnection(normalizedBookPath, activeBookConnections, markerConnections);
        throw exception;
      }
    }
  }

  static void recordConnectionClosed(@Nullable Path normalizedBookPath) {
    recordConnectionClosed(normalizedBookPath, true);
  }

  static void recordConnectionClosed(
      @Nullable Path normalizedBookPath, boolean publishesActivityMarker) {
    int remainingConnections = ACTIVE_CONNECTIONS.decrementAndGet();
    if (remainingConnections < 0) {
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException("SQLite active connection count underflow.");
    }
    if (normalizedBookPath == null) {
      return;
    }
    AtomicInteger activeBookConnections = ACTIVE_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    if (activeBookConnections == null) {
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite active connection registry missing the normalized book path entry for "
              + normalizedBookPath
              + ".");
    }
    int remainingBookConnections = activeBookConnections.decrementAndGet();
    if (remainingBookConnections < 0) {
      activeBookConnections.incrementAndGet();
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite active connection count underflow for normalized book path "
              + normalizedBookPath
              + ".");
    }
    if (!publishesActivityMarker) {
      if (remainingBookConnections == 0) {
        ACTIVE_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, activeBookConnections);
      }
      return;
    }
    AtomicInteger markerConnections = MARKER_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    if (markerConnections == null) {
      activeBookConnections.incrementAndGet();
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite activity-marker registry missing the normalized book path entry for "
              + normalizedBookPath
              + ".");
    }
    int remainingMarkerConnections = markerConnections.decrementAndGet();
    if (remainingMarkerConnections < 0) {
      markerConnections.incrementAndGet();
      activeBookConnections.incrementAndGet();
      ACTIVE_CONNECTIONS.incrementAndGet();
      throw new IllegalStateException(
          "SQLite activity-marker connection count underflow for normalized book path "
              + normalizedBookPath
              + ".");
    }
    if (remainingMarkerConnections == 0) {
      MARKER_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, markerConnections);
      SqliteBookActivityMarkers.deleteCurrentProcessMarker(normalizedBookPath);
    }
    if (remainingBookConnections == 0) {
      ACTIVE_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, activeBookConnections);
    }
  }

  static int activeConnectionCount() {
    return ACTIVE_CONNECTIONS.get();
  }

  static int activeConnectionCount(Path normalizedBookPath) {
    Objects.requireNonNull(normalizedBookPath, "normalizedBookPath");
    AtomicInteger activeBookConnections = ACTIVE_CONNECTIONS_BY_BOOK_PATH.get(normalizedBookPath);
    return activeBookConnections == null ? 0 : activeBookConnections.get();
  }

  static boolean hasExternalActiveConnections(Path normalizedBookPath) {
    return SqliteBookActivityMarkers.hasExternalLiveMarker(normalizedBookPath);
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

  private static void rollbackOpeningConnection(
      Path normalizedBookPath,
      AtomicInteger activeBookConnections,
      @Nullable AtomicInteger markerConnections) {
    if (markerConnections != null) {
      int remainingMarkerConnections = markerConnections.decrementAndGet();
      if (remainingMarkerConnections == 0) {
        MARKER_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, markerConnections);
      }
    }
    int remainingBookConnections = activeBookConnections.decrementAndGet();
    if (remainingBookConnections == 0) {
      ACTIVE_CONNECTIONS_BY_BOOK_PATH.remove(normalizedBookPath, activeBookConnections);
    }
    ACTIVE_CONNECTIONS.decrementAndGet();
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
