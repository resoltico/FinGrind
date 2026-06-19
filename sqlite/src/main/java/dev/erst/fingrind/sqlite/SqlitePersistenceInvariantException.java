package dev.erst.fingrind.sqlite;

/**
 * Signals that one SQLite persistence invariant leaked past deterministic pre-commit validation.
 */
public final class SqlitePersistenceInvariantException extends IllegalStateException {
  private static final long serialVersionUID = 1L;

  /** Creates one persistence-invariant breach with a user-facing explanation. */
  public SqlitePersistenceInvariantException(String message) {
    super(message);
  }

  /** Creates one persistence-invariant breach with a user-facing explanation and root cause. */
  public SqlitePersistenceInvariantException(String message, Throwable cause) {
    super(message, cause);
  }
}
