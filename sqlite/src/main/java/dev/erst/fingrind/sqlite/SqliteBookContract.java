package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.BookFormatContract;

/** Canonical SQLite book-format facts shared across SQLite-backed session adapters. */
final class SqliteBookContract {
  static final int APPLICATION_ID = BookFormatContract.APPLICATION_ID;
  static final int FORMAT_VERSION = BookFormatContract.FORMAT_VERSION;
  static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";

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
