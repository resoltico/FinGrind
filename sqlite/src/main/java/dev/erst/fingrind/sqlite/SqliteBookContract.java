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
  static final String JOURNAL_LINE_TABLE = "journal_line";
  static final String FISCAL_YEAR_CLOSE_POSTING_TABLE = "fiscal_year_close_posting";
  static final String FISCAL_YEAR_CLOSE_TABLE = "fiscal_year_close";
  static final String INTERIM_RESULT_SWEEP_POSTING_TABLE = "interim_result_sweep_posting";
  static final String INTERIM_RESULT_SWEEP_TABLE = "interim_result_sweep";
  static final String INTERIM_RESULT_SWEEP_TOTAL_TABLE = "interim_result_sweep_total";
  static final String POSTING_FACT_TABLE = "posting_fact";
  static final String POSTING_APPLIED_TAX_TABLE = "posting_applied_tax";
  static final String POSTING_FOREIGN_EXCHANGE_TABLE = "posting_foreign_exchange";
  static final String TAX_REGISTRATION_TABLE = "tax_registration";
  static final String TAX_REGISTRATION_CODE_TABLE = "tax_registration_code";

  static final SqliteBookStateReader BOOK_STATE_READER =
      new SqliteBookStateReader(
          APPLICATION_ID,
          FORMAT_VERSION,
          java.util.List.of(
              BOOK_META_TABLE,
              BOOK_IDENTITY_TABLE,
              ACCOUNT_TABLE,
              TAX_REGISTRATION_TABLE,
              TAX_REGISTRATION_CODE_TABLE,
              POSTING_FACT_TABLE,
              POSTING_APPLIED_TAX_TABLE,
              POSTING_FOREIGN_EXCHANGE_TABLE,
              JOURNAL_LINE_TABLE,
              INTERIM_RESULT_SWEEP_TABLE,
              INTERIM_RESULT_SWEEP_TOTAL_TABLE,
              INTERIM_RESULT_SWEEP_POSTING_TABLE,
              FISCAL_YEAR_CLOSE_TABLE,
              FISCAL_YEAR_CLOSE_POSTING_TABLE,
              AUDIT_EVENT_TABLE));

  private SqliteBookContract() {}
}
