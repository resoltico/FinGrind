package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.sqlite.internal.SqliteNativeCallAdapter;
import dev.erst.fingrind.sqlite.internal.SqliteNativeIntCalls;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Process-scoped bootstrap and publication of the SQLite native API bundle. */
final class SqliteNativeBootstrap {
  private static final java.lang.foreign.Linker LINKER = java.lang.foreign.Linker.nativeLinker();
  private static final String STRLEN_SYMBOL = "strlen";
  private static final java.lang.foreign.FunctionDescriptor STRLEN_DESCRIPTOR =
      java.lang.foreign.FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS);
  private static final AtomicReference<Optional<SqliteVerifiedLibrarySnapshot>>
      VERIFIED_LIBRARY_SNAPSHOT = new AtomicReference<>(Optional.empty());
  private static final AtomicBoolean RUNTIME_RELEASE_HOOK_INSTALLED = new AtomicBoolean();

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

  static void shutdownQuietly(MethodHandle sqlite3Shutdown) {
    shutdownQuietly(sqlite3Shutdown, SqliteBestEffort::reportCleanupFailure);
  }

  static void shutdownQuietly(MethodHandle sqlite3Shutdown, SqliteBestEffort.Reporter reporter) {
    Objects.requireNonNull(sqlite3Shutdown, "sqlite3Shutdown");
    Objects.requireNonNull(reporter, "reporter");
    try {
      shutdown(sqlite3Shutdown);
    } catch (RuntimeException exception) {
      reporter.report("shutting down the process-scoped SQLite runtime", exception);
    }
  }

  private static void shutdown(MethodHandle sqlite3Shutdown) {
    int resultCode =
        SqliteNativeCallAdapter.adapt(SqliteNativeIntCalls.NoArgIntCall.class, sqlite3Shutdown)
            .invoke();
    if (resultCode != SqliteNativeResultCode.code("OK")) {
      throw new IllegalStateException(
          "SQLite process-scoped runtime shutdown returned "
              + SqliteNativeResultCode.resultName(resultCode)
              + ".");
    }
  }

  /** Releases one initialized process-scoped native API and its verified library snapshot. */
  static void releaseProcessScopedRuntime() {
    SqliteNativeRuntimeRelease.release(
        VERIFIED_LIBRARY_SNAPSHOT.getAndSet(Optional.empty()).orElse(null),
        () -> shutdown(SqliteApiHolder.INSTANCE.sqlite3Shutdown()),
        () -> SqliteApiHolder.INSTANCE.libraryArena().close(),
        SqliteBestEffort::reportCleanupFailure);
  }

  /** Lazily publishes the process-scoped SQLite native API bundle on first use. */
  private static final class SqliteApiHolder {
    private static final SqliteNativeApi INSTANCE =
        SqliteNativeApiLoader.loadApi(
            snapshot -> {
              VERIFIED_LIBRARY_SNAPSHOT.set(Optional.of(snapshot));
              installRuntimeReleaseHook();
            });

    private SqliteApiHolder() {}
  }

  /** Installs one process-end fallback for callers that do not explicitly release the runtime. */
  static void installRuntimeReleaseHook() {
    installRuntimeReleaseHookOnce(
        RUNTIME_RELEASE_HOOK_INSTALLED,
        () ->
            Runtime.getRuntime()
                .addShutdownHook(
                    Thread.ofPlatform() // NOPMD - the JVM shutdown-hook API requires a platform
                        // thread.
                        .name("fingrind-sqlite-runtime-release")
                        .unstarted(SqliteNativeBootstrap::releaseProcessScopedRuntime)));
  }

  static boolean installRuntimeReleaseHookOnce(
      AtomicBoolean hookInstalled, Runnable shutdownHookRegistration) {
    if (!Objects.requireNonNull(hookInstalled, "hookInstalled").compareAndSet(false, true)) {
      return false;
    }
    Objects.requireNonNull(shutdownHookRegistration, "shutdownHookRegistration").run();
    return true;
  }

  /** Lazily resolves the process-global C runtime {@code strlen} symbol on first use. */
  private static final class StrlenHolder {
    private static final MethodHandle INSTANCE =
        SqliteNativeApiBindings.downcall(LINKER.defaultLookup(), STRLEN_SYMBOL, STRLEN_DESCRIPTOR);

    private StrlenHolder() {}
  }
}
