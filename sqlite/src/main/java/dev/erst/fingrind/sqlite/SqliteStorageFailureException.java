package dev.erst.fingrind.sqlite;

/** Signals that one SQLite storage operation failed after the runtime was already available. */
public final class SqliteStorageFailureException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  /** Creates a storage failure with a user-facing explanation. */
  public SqliteStorageFailureException(String message) {
    super(message);
  }

  /** Creates a storage failure with a user-facing explanation and root cause. */
  public SqliteStorageFailureException(String message, Throwable cause) {
    super(message, cause);
  }
}
