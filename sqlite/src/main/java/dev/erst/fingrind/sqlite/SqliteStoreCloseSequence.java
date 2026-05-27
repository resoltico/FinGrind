package dev.erst.fingrind.sqlite;

import org.jspecify.annotations.Nullable;

/**
 * Closes one SQLite database handle and its session-secret cleanup action in deterministic order.
 */
final class SqliteStoreCloseSequence implements AutoCloseable {
  /** Closes one session-secret cleanup action after database shutdown completes or fails. */
  @FunctionalInterface
  interface SessionSecretCloseAction {
    /** Closes the session-secret cleanup action for one SQLite store lifecycle. */
    void close();
  }

  private final @Nullable SessionSecretCloseAction sessionSecretCloseAction;
  private final @Nullable SqliteNativeDatabase database;

  /** Creates one deterministic close sequence for an optional database and session secret. */
  SqliteStoreCloseSequence(
      @Nullable SessionSecretCloseAction sessionSecretCloseAction,
      @Nullable SqliteNativeDatabase database) {
    this.sessionSecretCloseAction = sessionSecretCloseAction;
    this.database = database;
  }

  /** Closes the database first, then the session secret, preserving any primary failure. */
  @Override
  public void close() {
    Throwable databaseFailure = closeDatabaseFailure();
    Throwable sessionSecretFailure = closeSessionSecretFailure();
    if (databaseFailure != null && sessionSecretFailure != null) {
      databaseFailure.addSuppressed(sessionSecretFailure);
    }
    Throwable primaryFailure = databaseFailure != null ? databaseFailure : sessionSecretFailure;
    if (primaryFailure != null) {
      if (primaryFailure instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw (Error) primaryFailure;
    }
  }

  private @Nullable Throwable closeDatabaseFailure() {
    if (database == null) {
      return null;
    }
    try {
      database.close();
      return null;
    } catch (RuntimeException | Error exception) {
      return exception;
    }
  }

  private @Nullable Throwable closeSessionSecretFailure() {
    if (sessionSecretCloseAction == null) {
      return null;
    }
    try {
      sessionSecretCloseAction.close();
      return null;
    } catch (RuntimeException | Error exception) {
      return exception;
    }
  }
}
