package dev.erst.fingrind.sqlite;

import static java.lang.System.Logger.Level.WARNING;

import java.util.Objects;

/** Reports intentionally non-fatal SQLite cleanup failures without changing the primary outcome. */
final class SqliteBestEffort {
  private static final System.Logger LOGGER = System.getLogger(SqliteBestEffort.class.getName());

  private SqliteBestEffort() {}

  static void reportCleanupFailure(String action, Exception exception) {
    reportCleanupFailure(action, exception, SqliteBestEffort::logCleanupFailure);
  }

  static void reportCleanupFailure(String action, Exception exception, Reporter reporter) {
    Objects.requireNonNull(action, "action");
    Objects.requireNonNull(exception, "exception");
    Objects.requireNonNull(reporter, "reporter");
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
}
