package dev.erst.fingrind.sqlite;

/** Stable on-disk lifecycle state derived from one selected SQLite book file. */
enum SqliteBookState {
  BLANK_SQLITE,
  INITIALIZED_FINGRIND,
  FOREIGN_SQLITE,
  UNSUPPORTED_FINGRIND_VERSION,
  INCOMPLETE_FINGRIND
}
