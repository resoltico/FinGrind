package dev.erst.fingrind.sqlite;

import java.util.Objects;

/** SQLite open policies mapped from FinGrind command-level access intents. */
enum SqliteStoreAccessMode {
  /** Opens one existing book in read-only mode. */
  READ_ONLY(SqliteNativeOpenMode.READ_ONLY, true, false, false),
  /** Opens one existing book for read/write mutations without creating new files. */
  READ_WRITE_EXISTING(SqliteNativeOpenMode.READ_WRITE_EXISTING, false, true, false),
  /** Opens one book for read/write access and creates the file when needed. */
  READ_WRITE_CREATE(SqliteNativeOpenMode.READ_WRITE_CREATE, false, true, true),
  /** Defers file creation until a plan mutation actually requires it. */
  PLAN_EXECUTION(SqliteNativeOpenMode.READ_WRITE_CREATE, false, true, true);

  private final SqliteNativeOpenMode nativeOpenMode;
  private final boolean queryOnly;
  private final boolean writable;
  private final boolean createsFiles;

  SqliteStoreAccessMode(
      SqliteNativeOpenMode nativeOpenMode,
      boolean queryOnly,
      boolean writable,
      boolean createsFiles) {
    this.nativeOpenMode = Objects.requireNonNull(nativeOpenMode, "nativeOpenMode");
    this.queryOnly = queryOnly;
    this.writable = writable;
    this.createsFiles = createsFiles;
  }

  SqliteNativeOpenMode nativeOpenMode() {
    return nativeOpenMode;
  }

  int queryOnlyPragmaValue() {
    return queryOnly ? 1 : 0;
  }

  boolean writesJournalMode() {
    return writable;
  }

  boolean preservesMissingBookStateUntilMutation() {
    return createsFiles;
  }

  void requireWritableMutation() {
    if (!writable) {
      throw new IllegalStateException(
          "This FinGrind SQLite session is read-only and cannot mutate the book.");
    }
  }

  void requireWritableInitialization() {
    if (!createsFiles) {
      throw new IllegalStateException(
          "This FinGrind SQLite session cannot initialize or create a book file.");
    }
  }
}
