package dev.erst.fingrind.sqlite;

/** Public SQLite book-session intent selected by the caller. */
public enum SqliteBookSessionMode {
  /** Opens one existing book in read-only mode. */
  READ_ONLY,
  /** Opens one existing book for read/write mutations without creating new files. */
  READ_WRITE_EXISTING,
  /** Opens one book for read/write access and creates the file when needed. */
  READ_WRITE_CREATE,
  /** Defers file creation until a plan mutation actually requires it. */
  PLAN_EXECUTION,
}
