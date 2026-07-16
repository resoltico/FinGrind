package dev.erst.fingrind.sqlite;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Best-effort deletion of temporary SQLite maintenance files. */
final class SqliteFileCleanup {
  private SqliteFileCleanup() {}

  static void deleteQuietly(Path path) {
    deleteQuietly(path, "deleting one temporary SQLite maintenance path");
  }

  static void deleteQuietly(Path path, String action) {
    deleteQuietly(path, action, SqliteBestEffort::reportCleanupFailure);
  }

  static void deleteQuietly(Path path, String action, SqliteBestEffort.Reporter reporter) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      reporter.report(action, exception);
    }
  }
}
