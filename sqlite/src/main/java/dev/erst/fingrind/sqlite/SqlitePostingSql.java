package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;

/** Behavioral SQL assembly seam over the reviewed posting SQL literal catalog. */
final class SqlitePostingSql {
  static final String INITIALIZED_AT_META_KEY = SqlitePostingMetadataSql.INITIALIZED_AT_META_KEY;
  static final String SCHEMA_FINGERPRINT_META_KEY =
      SqlitePostingMetadataSql.SCHEMA_FINGERPRINT_META_KEY;

  static final String USER_SCHEMA_EXISTS = SqlitePostingMetadataSql.USER_SCHEMA_EXISTS;
  static final String TABLE_EXISTS = SqlitePostingMetadataSql.TABLE_EXISTS;
  static final String BOOK_INITIALIZED_EXISTS = SqlitePostingMetadataSql.BOOK_INITIALIZED_EXISTS;
  static final String FIND_BOOK_INITIALIZED_AT = SqlitePostingMetadataSql.FIND_BOOK_INITIALIZED_AT;
  static final String FIND_BOOK_META_VALUE = SqlitePostingMetadataSql.FIND_BOOK_META_VALUE;
  static final String FIND_BOOK_IDENTITY_CORE = SqlitePostingMetadataSql.FIND_BOOK_IDENTITY_CORE;
  static final String PRAGMA_INTEGRITY_CHECK = SqlitePostingIntegritySql.PRAGMA_INTEGRITY_CHECK;
  static final String PRAGMA_FOREIGN_KEY_CHECK = SqlitePostingIntegritySql.PRAGMA_FOREIGN_KEY_CHECK;
  static final int EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT =
      SqlitePostingIntegritySql.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT;
  static final String LOAD_CANONICAL_SCHEMA_OBJECTS =
      SqlitePostingIntegritySql.LOAD_CANONICAL_SCHEMA_OBJECTS;
  static final String LOAD_NON_CANONICAL_SCHEMA_OBJECTS =
      SqlitePostingIntegritySql.LOAD_NON_CANONICAL_SCHEMA_OBJECTS;
  static final String FIND_ACCOUNT_BY_CODE = SqlitePostingReadWriteSql.FIND_ACCOUNT_BY_CODE;
  static final String FIND_POSTING_BY_IDEMPOTENCY =
      SqlitePostingReadWriteSql.FIND_POSTING_BY_IDEMPOTENCY;
  static final String FIND_POSTING_BY_ID = SqlitePostingReadWriteSql.FIND_POSTING_BY_ID;
  static final String FIND_REVERSAL_FOR = SqlitePostingReadWriteSql.FIND_REVERSAL_FOR;
  static final String EXISTS_POSTING_BY_IDEMPOTENCY =
      SqlitePostingReadWriteSql.EXISTS_POSTING_BY_IDEMPOTENCY;
  static final String EXISTS_REVERSAL_FOR = SqlitePostingReadWriteSql.EXISTS_REVERSAL_FOR;
  static final String LOAD_LINES = SqlitePostingReadWriteSql.LOAD_LINES;
  static final String LOAD_SOURCE_DOCUMENTS = SqlitePostingReadWriteSql.LOAD_SOURCE_DOCUMENTS;
  static final String LOAD_APPROVALS = SqlitePostingReadWriteSql.LOAD_APPROVALS;
  static final String LOAD_POSTING_FOREIGN_EXCHANGE =
      SqliteForeignExchangeSql.LOAD_POSTING_FOREIGN_EXCHANGE;
  static final String INSERT_POSTING_FACT = SqlitePostingReadWriteSql.INSERT_POSTING_FACT;
  static final String INSERT_JOURNAL_LINE = SqlitePostingReadWriteSql.INSERT_JOURNAL_LINE;
  static final String INSERT_POSTING_SOURCE_DOCUMENT =
      SqlitePostingReadWriteSql.INSERT_POSTING_SOURCE_DOCUMENT;
  static final String INSERT_POSTING_APPROVAL = SqlitePostingReadWriteSql.INSERT_POSTING_APPROVAL;
  static final String INSERT_POSTING_FOREIGN_EXCHANGE =
      SqliteForeignExchangeSql.INSERT_POSTING_FOREIGN_EXCHANGE;
  static final String INSERT_AUDIT_EVENT = SqlitePostingReadWriteSql.INSERT_AUDIT_EVENT;
  static final String FIND_LATEST_POSTING_EFFECTIVE_DATE =
      SqlitePostingQuerySql.FIND_LATEST_POSTING_EFFECTIVE_DATE;
  static final String LOAD_ALL_ACCOUNTS = SqlitePostingReadWriteSql.LOAD_ALL_ACCOUNTS;
  static final String LOAD_POSTINGS_IN_RANGE = SqlitePostingReadWriteSql.LOAD_POSTINGS_IN_RANGE;
  static final String CREATE_PENDING_JOURNAL_LINE =
      SqlitePendingJournalSql.CREATE_PENDING_JOURNAL_LINE;
  static final String CLEAR_PENDING_JOURNAL_LINE =
      SqlitePendingJournalSql.CLEAR_PENDING_JOURNAL_LINE;
  static final String INSERT_PENDING_JOURNAL_LINE =
      SqlitePendingJournalSql.INSERT_PENDING_JOURNAL_LINE;
  static final String VALID_PENDING_JOURNAL_LINE =
      SqlitePendingJournalSql.VALID_PENDING_JOURNAL_LINE;
  static final String PERSIST_PENDING_JOURNAL_LINE =
      SqlitePendingJournalSql.PERSIST_PENDING_JOURNAL_LINE;
  static final String FIND_UNBALANCED_POSTING = SqlitePostingIntegritySql.FIND_UNBALANCED_POSTING;
  static final String FIND_POSTING_WITHOUT_JOURNAL_LINES =
      SqlitePostingIntegritySql.FIND_POSTING_WITHOUT_JOURNAL_LINES;
  static final String FIND_LATE_OPENING_BALANCE_POSTING =
      SqlitePostingIntegritySql.FIND_LATE_OPENING_BALANCE_POSTING;
  static final String FIND_OPENING_BALANCE_NOMINAL_ACCOUNT =
      SqlitePostingIntegritySql.FIND_OPENING_BALANCE_NOMINAL_ACCOUNT;
  static final String FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT =
      SqlitePostingIntegritySql.FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT;
  static final String FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD =
      SqlitePostingIntegritySql.FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD;
  static final String FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING =
      SqlitePostingIntegritySql.FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING;
  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK =
      SqlitePostingIntegritySql.FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK;
  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT =
      SqlitePostingIntegritySql.FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT;
  static final String FIND_INVALID_FISCAL_YEAR_CLOSE_LINK =
      SqlitePostingIntegritySql.FIND_INVALID_FISCAL_YEAR_CLOSE_LINK;
  static final String FIND_INVALID_FISCAL_YEAR_CLOSE_TARGET_ACCOUNT =
      SqlitePostingIntegritySql.FIND_INVALID_FISCAL_YEAR_CLOSE_TARGET_ACCOUNT;
  static final String LOAD_PERSISTED_MONEY_AUDIT_ROWS =
      SqlitePostingIntegritySql.LOAD_PERSISTED_MONEY_AUDIT_ROWS;
  static final String FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY =
      SqlitePostingIntegritySql.FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY;
  static final String INSERT_BOOK_META_VALUE = SqlitePostingMetadataSql.INSERT_BOOK_META_VALUE;
  static final String INSERT_BOOK_IDENTITY = SqlitePostingMetadataSql.INSERT_BOOK_IDENTITY;
  static final String UPSERT_ACCOUNT = SqlitePostingReadWriteSql.UPSERT_ACCOUNT;

  static String listAccounts() {
    return SqlitePostingQuerySql.listAccounts();
  }

  static String findAccountsByCodeCount(int accountCount) {
    return SqlitePostingQuerySql.findAccountsByCodeCount(accountCount);
  }

  static String listPostings(PostingHistoryQuery query) {
    return SqlitePostingQuerySql.listPostings(query);
  }

  static String loadAccountLinesForBalance(AccountBalanceCriteria query) {
    return SqlitePostingQuerySql.loadAccountLinesForBalance(query);
  }

  static String loadTrialBalanceLines(TrialBalanceCriteria query) {
    return SqlitePostingQuerySql.loadTrialBalanceLines(query);
  }

  static String loadAccountTotals(TrialBalanceCriteria query) {
    return SqlitePostingQuerySql.loadAccountTotals(query);
  }

  static String loadAccountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    return SqlitePostingQuerySql.loadAccountTotals(effectiveDateRange, postingCoverage);
  }

  static String loadPeriodSummaryLines(PeriodSummaryCriteria query) {
    return SqlitePostingQuerySql.loadPeriodSummaryLines(query);
  }

  static String listPostingsForAccountLedger(AccountLedgerCriteria query) {
    return SqlitePostingQuerySql.listPostingsForAccountLedger(query);
  }

  private SqlitePostingSql() {}
}
