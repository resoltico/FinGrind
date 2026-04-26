package dev.erst.fingrind.sqlite;

import static java.lang.System.Logger.Level.WARNING;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Reports intentionally non-fatal SQLite cleanup failures without changing the primary outcome. */
final class SqliteBestEffort {
  private static final System.Logger LOGGER = System.getLogger(SqliteBestEffort.class.getName());
  private static final AtomicReference<Reporter> REPORTER =
      new AtomicReference<>(SqliteBestEffort::logCleanupFailure);

  private SqliteBestEffort() {}

  static void reportCleanupFailure(String action, Exception exception) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(exception, "exception");
    Reporter reporter = REPORTER.get();
    try {
      reporter.report(action, exception);
    } catch (RuntimeException reporterFailure) {
      LOGGER.log(
          WARNING,
          "SQLite cleanup reporter failed while handling one best-effort cleanup failure.",
          reporterFailure);
      logCleanupFailure(action, exception);
    }
  }

  static ReporterOverride replaceReporterForTesting(Reporter reporter) {
    Objects.requireNonNull(reporter, "reporter");
    Reporter previousReporter = REPORTER.getAndSet(reporter);
    return () -> REPORTER.set(previousReporter);
  }

  private static void logCleanupFailure(String action, Exception exception) {
    LOGGER.log(
        WARNING,
        "SQLite best-effort cleanup failed during " + action + "; preserving the primary outcome.",
        exception);
  }

  /** Receives one reported best-effort cleanup failure. */
  @FunctionalInterface
  interface Reporter {
    /** Handles one non-fatal cleanup failure while preserving the primary outcome. */
    void report(String action, Exception exception);
  }

  /** Restores the prior cleanup-failure reporter after a test override. */
  @FunctionalInterface
  interface ReporterOverride extends AutoCloseable {
    /** Restores the previously active reporter. */
    @Override
    void close();
  }
}
