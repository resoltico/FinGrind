package dev.erst.fingrind.sqlite;

/** Signals an authentication or integrity failure while reading a protected SQLite book. */
public final class SqliteProtectedBookVerificationException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  /**
   * Creates the typed boundary failure without exposing a native SQLite diagnostic to callers.
   *
   * @param cause the internal authentication or integrity failure
   */
  public SqliteProtectedBookVerificationException(Throwable cause) {
    super("Protected-book authentication or integrity verification failed.", cause);
  }
}
