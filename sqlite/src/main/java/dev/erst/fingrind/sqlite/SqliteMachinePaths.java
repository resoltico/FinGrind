package dev.erst.fingrind.sqlite;

import java.nio.file.Path;
import java.util.Objects;

/** Owns canonical path values embedded in SQLite-originated machine diagnostics. */
final class SqliteMachinePaths {
  private SqliteMachinePaths() {}

  static String absoluteValue(Path path) {
    return Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
  }
}
