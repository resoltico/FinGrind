package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookMigrationPolicy;
import java.util.Objects;

/** Canonical migration planner for FinGrind SQLite book-format evolution. */
final class SqliteBookMigrationPlanner {
  private final int currentBookFormatVersion;
  private final BookMigrationPolicy policy;

  SqliteBookMigrationPlanner(int currentBookFormatVersion, BookMigrationPolicy policy) {
    if (currentBookFormatVersion < 1) {
      throw new IllegalArgumentException("Current book format version must be at least 1.");
    }
    this.currentBookFormatVersion = currentBookFormatVersion;
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  int currentBookFormatVersion() {
    return currentBookFormatVersion;
  }

  BookMigrationPolicy policy() {
    return policy;
  }
}
