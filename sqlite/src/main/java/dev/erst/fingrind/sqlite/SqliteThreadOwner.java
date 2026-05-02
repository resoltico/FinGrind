package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Owns one thread-confined SQLite resource and rejects cross-thread access explicitly. */
final class SqliteThreadOwner {
  private final String resourceLabel;
  private final long ownerThreadId;
  private final String ownerThreadName;

  SqliteThreadOwner(String resourceLabel) {
    this.resourceLabel = requireNonBlank(resourceLabel, "resourceLabel");
    Thread ownerThread =
        Thread.currentThread(); // NOPMD - thread confinement owner capture is required here.
    this.ownerThreadId = ownerThread.threadId();
    this.ownerThreadName = ownerThread.getName();
  }

  void requireOwnerThread() {
    Thread currentThread =
        Thread.currentThread(); // NOPMD - current-thread inspection enforces confinement here.
    if (currentThread.threadId() != ownerThreadId) {
      throw new IllegalStateException(
          resourceLabel
              + " is thread-confined and is owned by thread '"
              + ownerThreadName
              + "' but was accessed from thread '"
              + currentThread.getName()
              + "'.");
    }
  }

  private static String requireNonBlank(String value, String fieldName) {
    Objects.requireNonNull(value, fieldName + " must not be null");
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return trimmed;
  }
}
