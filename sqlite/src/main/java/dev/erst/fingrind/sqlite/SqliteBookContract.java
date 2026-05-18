package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.runtime.BookFormatContract;

/** Canonical SQLite book-format facts shared across SQLite-backed session adapters. */
final class SqliteBookContract {
  static final int APPLICATION_ID = BookFormatContract.APPLICATION_ID;
  static final int FORMAT_VERSION = BookFormatContract.FORMAT_VERSION;
  static final String NOT_INITIALIZED_BOOK_MESSAGE =
      "The selected SQLite file is not initialized as a FinGrind book.";

  static final String ACCOUNT_TABLE = "account";
  static final String AUDIT_EVENT_TABLE = "audit_event";
  static final String BOOK_IDENTITY_TABLE = "book_identity";
  static final String BOOK_META_TABLE = "book_meta";
  static final String BOOK_POLICY_TABLE = "book_policy";
  static final String ENTITY_PROFILE_TABLE = "entity_profile";
  static final String JOURNAL_LINE_TABLE = "journal_line";
  static final String PERIOD_CLOSE_POSTING_TABLE = "period_close_posting";
  static final String PERIOD_CLOSE_TABLE = "period_close";
  static final String PERIOD_CLOSE_TOTAL_TABLE = "period_close_total";
  static final String POSTING_FACT_TABLE = "posting_fact";

  static final SqliteBookStateReader BOOK_STATE_READER =
      new SqliteBookStateReader(
          APPLICATION_ID,
          FORMAT_VERSION,
          java.util.List.of(
              BOOK_META_TABLE,
              BOOK_IDENTITY_TABLE,
              ENTITY_PROFILE_TABLE,
              BOOK_POLICY_TABLE,
              ACCOUNT_TABLE,
              POSTING_FACT_TABLE,
              JOURNAL_LINE_TABLE,
              PERIOD_CLOSE_TABLE,
              PERIOD_CLOSE_TOTAL_TABLE,
              PERIOD_CLOSE_POSTING_TABLE,
              AUDIT_EVENT_TABLE));

  private SqliteBookContract() {}
}
