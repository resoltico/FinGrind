package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Marks one intentionally ignored cleanup failure in best-effort teardown paths. */
final class SqliteBestEffort {
  private SqliteBestEffort() {}

  static void ignore(Exception exception) {
    Objects.requireNonNull(exception, "exception");
  }
}
