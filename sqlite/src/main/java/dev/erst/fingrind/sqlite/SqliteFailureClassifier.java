package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** Classifies SQLite-related runtime failures for CLI hint generation. */
public final class SqliteFailureClassifier {
  private SqliteFailureClassifier() {}

  /** Stable hint category for one runtime failure. */
  public enum Category {
    MANAGED_RUNTIME,
    STORAGE,
    OTHER
  }

  /** Returns the most specific SQLite runtime-failure category for the supplied throwable. */
  public static Category classify(Throwable throwable) {
    Objects.requireNonNull(throwable, "throwable");
    for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
      if (cause instanceof ManagedSqliteRuntimeUnavailableException
          || cause instanceof UnsupportedSqliteVersionException
          || cause instanceof UnsupportedSqliteMultipleCiphersVersionException
          || cause instanceof UnsupportedSqliteCompileOptionsException) {
        return Category.MANAGED_RUNTIME;
      }
      if (cause instanceof SqliteStorageFailureException
          || cause instanceof SqliteNativeException) {
        return Category.STORAGE;
      }
    }
    return Category.OTHER;
  }
}
