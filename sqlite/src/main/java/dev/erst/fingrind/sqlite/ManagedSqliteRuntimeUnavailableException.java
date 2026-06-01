package dev.erst.fingrind.sqlite;

/** Signals that the managed SQLite runtime could not be located or used on this host. */
public final class ManagedSqliteRuntimeUnavailableException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  /** Creates a runtime-unavailable failure with a user-facing explanation. */
  public ManagedSqliteRuntimeUnavailableException(String message) {
    super(message);
  }

  /** Creates a runtime-unavailable failure with a user-facing explanation and root cause. */
  public ManagedSqliteRuntimeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
