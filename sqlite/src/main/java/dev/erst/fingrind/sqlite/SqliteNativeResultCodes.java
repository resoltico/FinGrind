package dev.erst.fingrind.sqlite;

/** Canonical SQLite integer result codes consumed by the FinGrind native bridge. */
final class SqliteNativeResultCodes {
  static final int OK = 0;
  static final int ROW = 100;
  static final int DONE = 101;

  static final int CONSTRAINT_UNIQUE = 2067;
  static final int CONSTRAINT_PRIMARYKEY = 1555;
  static final int CONSTRAINT_DATATYPE = 3091;
  static final int CONSTRAINT_FOREIGNKEY = 787;
  static final int CANTOPEN = 14;
  static final int CANTOPEN_ISDIR = 526;
  static final int NOTADB = 26;

  private SqliteNativeResultCodes() {}
}
