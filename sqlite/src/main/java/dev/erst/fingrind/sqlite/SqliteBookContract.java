package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookMigrationPolicy;

/** Canonical SQLite book-format facts shared across SQLite-backed session adapters. */
final class SqliteBookContract {
  static final int APPLICATION_ID = 1_179_079_236; // "FGRD"
  private static final SqliteBookMigrationPlanner BOOK_MIGRATION_PLANNER =
      new SqliteBookMigrationPlanner(1, BookMigrationPolicy.SEQUENTIAL_IN_PLACE);
  static final int FORMAT_VERSION = BOOK_MIGRATION_PLANNER.currentBookFormatVersion();
  static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";
  static final BookMigrationPolicy MIGRATION_POLICY = BOOK_MIGRATION_PLANNER.policy();

  private static final String ACCOUNT_TABLE = "account";
  private static final String BOOK_META_TABLE = "book_meta";
  private static final String JOURNAL_LINE_TABLE = "journal_line";
  private static final String POSTING_FACT_TABLE = "posting_fact";

  static final SqliteBookStateReader BOOK_STATE_READER =
      new SqliteBookStateReader(
          APPLICATION_ID,
          FORMAT_VERSION,
          ACCOUNT_TABLE,
          BOOK_META_TABLE,
          JOURNAL_LINE_TABLE,
          POSTING_FACT_TABLE);

  private SqliteBookContract() {}
}
