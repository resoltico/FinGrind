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
  static final String INITIALIZED_AT_META_KEY = SqlitePostingSqlLiterals.INITIALIZED_AT_META_KEY;
  static final String SCHEMA_FINGERPRINT_META_KEY =
      SqlitePostingSqlLiterals.SCHEMA_FINGERPRINT_META_KEY;

  static final int COL_POSTING_ID = SqlitePostingSqlLiterals.COL_POSTING_ID;
  static final int COL_POSTING_KIND = SqlitePostingSqlLiterals.COL_POSTING_KIND;
  static final int COL_POSTING_ORIGIN_KIND = SqlitePostingSqlLiterals.COL_POSTING_ORIGIN_KIND;
  static final int COL_EFFECTIVE_DATE = SqlitePostingSqlLiterals.COL_EFFECTIVE_DATE;
  static final int COL_RECORDED_AT = SqlitePostingSqlLiterals.COL_RECORDED_AT;
  static final int COL_ACTOR_ID = SqlitePostingSqlLiterals.COL_ACTOR_ID;
  static final int COL_ACTOR_TYPE = SqlitePostingSqlLiterals.COL_ACTOR_TYPE;
  static final int COL_COMMAND_ID = SqlitePostingSqlLiterals.COL_COMMAND_ID;
  static final int COL_IDEMPOTENCY_KEY = SqlitePostingSqlLiterals.COL_IDEMPOTENCY_KEY;
  static final int COL_CAUSATION_ID = SqlitePostingSqlLiterals.COL_CAUSATION_ID;
  static final int COL_CORRELATION_ID = SqlitePostingSqlLiterals.COL_CORRELATION_ID;
  static final int COL_REASON = SqlitePostingSqlLiterals.COL_REASON;
  static final int COL_SOURCE_CHANNEL = SqlitePostingSqlLiterals.COL_SOURCE_CHANNEL;
  static final int COL_PRIOR_POSTING_ID = SqlitePostingSqlLiterals.COL_PRIOR_POSTING_ID;

  static final int COL_LINE_ACCOUNT_CODE = SqlitePostingSqlLiterals.COL_LINE_ACCOUNT_CODE;
  static final int COL_LINE_ENTRY_SIDE = SqlitePostingSqlLiterals.COL_LINE_ENTRY_SIDE;
  static final int COL_LINE_CURRENCY_CODE = SqlitePostingSqlLiterals.COL_LINE_CURRENCY_CODE;
  static final int COL_LINE_AMOUNT_MINOR = SqlitePostingSqlLiterals.COL_LINE_AMOUNT_MINOR;

  static final int COL_SOURCE_DOCUMENT_ID = SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_ID;
  static final int COL_SOURCE_DOCUMENT_TYPE = SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_TYPE;
  static final int COL_SOURCE_DOCUMENT_DATE = SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_DATE;
  static final int COL_SOURCE_DOCUMENT_CAPTURED_AT =
      SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_CAPTURED_AT;
  static final int COL_SOURCE_DOCUMENT_STORAGE_LOCATOR =
      SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_STORAGE_LOCATOR;
  static final int COL_SOURCE_DOCUMENT_CONTENT_SHA256 =
      SqlitePostingSqlLiterals.COL_SOURCE_DOCUMENT_CONTENT_SHA256;

  static final int COL_APPROVAL_ID = SqlitePostingSqlLiterals.COL_APPROVAL_ID;
  static final int COL_APPROVAL_TYPE = SqlitePostingSqlLiterals.COL_APPROVAL_TYPE;
  static final int COL_APPROVER_ID = SqlitePostingSqlLiterals.COL_APPROVER_ID;
  static final int COL_APPROVER_TYPE = SqlitePostingSqlLiterals.COL_APPROVER_TYPE;
  static final int COL_APPROVAL_DECISION = SqlitePostingSqlLiterals.COL_APPROVAL_DECISION;
  static final int COL_APPROVED_AT = SqlitePostingSqlLiterals.COL_APPROVED_AT;

  static final int COL_ACCOUNT_CODE = SqlitePostingSqlLiterals.COL_ACCOUNT_CODE;
  static final int COL_ACCOUNT_NAME = SqlitePostingSqlLiterals.COL_ACCOUNT_NAME;
  static final int COL_ACCOUNT_TYPE = SqlitePostingSqlLiterals.COL_ACCOUNT_TYPE;
  static final int COL_ACCOUNT_ROLE = SqlitePostingSqlLiterals.COL_ACCOUNT_ROLE;
  static final int COL_ACCOUNT_NODE_KIND = SqlitePostingSqlLiterals.COL_ACCOUNT_NODE_KIND;
  static final int COL_ACCOUNT_PARENT_ACCOUNT_CODE =
      SqlitePostingSqlLiterals.COL_ACCOUNT_PARENT_ACCOUNT_CODE;
  static final int COL_ACCOUNT_FINANCIAL_POSITION_LINE_CLASSIFICATION =
      SqlitePostingSqlLiterals.COL_ACCOUNT_FINANCIAL_POSITION_LINE_CLASSIFICATION;
  static final int COL_ACCOUNT_PROFIT_AND_LOSS_LINE_CLASSIFICATION =
      SqlitePostingSqlLiterals.COL_ACCOUNT_PROFIT_AND_LOSS_LINE_CLASSIFICATION;
  static final int COL_ACCOUNT_ACTIVE = SqlitePostingSqlLiterals.COL_ACCOUNT_ACTIVE;
  static final int COL_ACCOUNT_DECLARED_AT = SqlitePostingSqlLiterals.COL_ACCOUNT_DECLARED_AT;
  static final int COL_REPORT_POSTING_ID = SqlitePostingSqlLiterals.COL_REPORT_POSTING_ID;
  static final int COL_REPORT_ENTRY_SIDE = SqlitePostingSqlLiterals.COL_REPORT_ENTRY_SIDE;
  static final int COL_REPORT_CURRENCY_CODE = SqlitePostingSqlLiterals.COL_REPORT_CURRENCY_CODE;
  static final int COL_REPORT_AMOUNT_MINOR = SqlitePostingSqlLiterals.COL_REPORT_AMOUNT_MINOR;
  static final int COL_TOTAL_CURRENCY_CODE = SqlitePostingSqlLiterals.COL_TOTAL_CURRENCY_CODE;
  static final int COL_TOTAL_DEBIT_MINOR = SqlitePostingSqlLiterals.COL_TOTAL_DEBIT_MINOR;
  static final int COL_TOTAL_CREDIT_MINOR = SqlitePostingSqlLiterals.COL_TOTAL_CREDIT_MINOR;

  static final String USER_SCHEMA_EXISTS = SqlitePostingSqlLiterals.USER_SCHEMA_EXISTS;
  static final String TABLE_EXISTS = SqlitePostingSqlLiterals.TABLE_EXISTS;
  static final String BOOK_INITIALIZED_EXISTS = SqlitePostingSqlLiterals.BOOK_INITIALIZED_EXISTS;
  static final String FIND_BOOK_INITIALIZED_AT = SqlitePostingSqlLiterals.FIND_BOOK_INITIALIZED_AT;
  static final String FIND_BOOK_META_VALUE = SqlitePostingSqlLiterals.FIND_BOOK_META_VALUE;
  static final String FIND_BOOK_IDENTITY_CORE = SqlitePostingSqlLiterals.FIND_BOOK_IDENTITY_CORE;
  static final String PRAGMA_INTEGRITY_CHECK = SqlitePostingSqlLiterals.PRAGMA_INTEGRITY_CHECK;
  static final String PRAGMA_FOREIGN_KEY_CHECK = SqlitePostingSqlLiterals.PRAGMA_FOREIGN_KEY_CHECK;
  static final int EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT =
      SqlitePostingSqlLiterals.EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT;
  static final String LOAD_CANONICAL_SCHEMA_OBJECTS =
      SqlitePostingSqlLiterals.LOAD_CANONICAL_SCHEMA_OBJECTS;
  static final String LOAD_NON_CANONICAL_SCHEMA_OBJECTS =
      SqlitePostingSqlLiterals.LOAD_NON_CANONICAL_SCHEMA_OBJECTS;
  static final String FIND_ACCOUNT_BY_CODE = SqlitePostingSqlLiterals.FIND_ACCOUNT_BY_CODE;
  static final String FIND_POSTING_BY_IDEMPOTENCY =
      SqlitePostingSqlLiterals.FIND_POSTING_BY_IDEMPOTENCY;
  static final String FIND_POSTING_BY_ID = SqlitePostingSqlLiterals.FIND_POSTING_BY_ID;
  static final String FIND_REVERSAL_FOR = SqlitePostingSqlLiterals.FIND_REVERSAL_FOR;
  static final String EXISTS_POSTING_BY_IDEMPOTENCY =
      SqlitePostingSqlLiterals.EXISTS_POSTING_BY_IDEMPOTENCY;
  static final String EXISTS_REVERSAL_FOR = SqlitePostingSqlLiterals.EXISTS_REVERSAL_FOR;
  static final String LOAD_LINES = SqlitePostingSqlLiterals.LOAD_LINES;
  static final String LOAD_SOURCE_DOCUMENTS = SqlitePostingSqlLiterals.LOAD_SOURCE_DOCUMENTS;
  static final String LOAD_APPROVALS = SqlitePostingSqlLiterals.LOAD_APPROVALS;
  static final String LOAD_ACCOUNT_LINES_FOR_BALANCE =
      SqlitePostingSqlLiterals.LOAD_ACCOUNT_LINES_FOR_BALANCE;
  static final String INSERT_POSTING_FACT = SqlitePostingSqlLiterals.INSERT_POSTING_FACT;
  static final String INSERT_JOURNAL_LINE = SqlitePostingSqlLiterals.INSERT_JOURNAL_LINE;
  static final String INSERT_POSTING_SOURCE_DOCUMENT =
      SqlitePostingSqlLiterals.INSERT_POSTING_SOURCE_DOCUMENT;
  static final String INSERT_POSTING_APPROVAL = SqlitePostingSqlLiterals.INSERT_POSTING_APPROVAL;
  static final String INSERT_AUDIT_EVENT = SqlitePostingSqlLiterals.INSERT_AUDIT_EVENT;
  static final String INSERT_PERIOD_RESULT_TRANSFER =
      SqlitePostingSqlLiterals.INSERT_PERIOD_RESULT_TRANSFER;
  static final String INSERT_PERIOD_RESULT_TRANSFER_TOTAL =
      SqlitePostingSqlLiterals.INSERT_PERIOD_RESULT_TRANSFER_TOTAL;
  static final String INSERT_PERIOD_RESULT_TRANSFER_POSTING =
      SqlitePostingSqlLiterals.INSERT_PERIOD_RESULT_TRANSFER_POSTING;
  static final String FIND_CLOSED_THROUGH_EFFECTIVE_DATE =
      SqlitePostingSqlLiterals.FIND_CLOSED_THROUGH_EFFECTIVE_DATE;
  static final String FIND_EARLIEST_POSTING_EFFECTIVE_DATE =
      SqlitePostingSqlLiterals.FIND_EARLIEST_POSTING_EFFECTIVE_DATE;
  static final String LOAD_ALL_ACCOUNTS = SqlitePostingSqlLiterals.LOAD_ALL_ACCOUNTS;
  static final String LOAD_POSTINGS_IN_RANGE = SqlitePostingSqlLiterals.LOAD_POSTINGS_IN_RANGE;
  static final String CREATE_PENDING_JOURNAL_LINE =
      SqlitePostingSqlLiterals.CREATE_PENDING_JOURNAL_LINE;
  static final String CLEAR_PENDING_JOURNAL_LINE =
      SqlitePostingSqlLiterals.CLEAR_PENDING_JOURNAL_LINE;
  static final String INSERT_PENDING_JOURNAL_LINE =
      SqlitePostingSqlLiterals.INSERT_PENDING_JOURNAL_LINE;
  static final String VALID_PENDING_JOURNAL_LINE =
      SqlitePostingSqlLiterals.VALID_PENDING_JOURNAL_LINE;
  static final String PERSIST_PENDING_JOURNAL_LINE =
      SqlitePostingSqlLiterals.PERSIST_PENDING_JOURNAL_LINE;
  static final String FIND_UNBALANCED_POSTING = SqlitePostingSqlLiterals.FIND_UNBALANCED_POSTING;
  static final String FIND_POSTING_WITHOUT_JOURNAL_LINES =
      SqlitePostingSqlLiterals.FIND_POSTING_WITHOUT_JOURNAL_LINES;
  static final String FIND_LATE_OPENING_BALANCE_POSTING =
      SqlitePostingSqlLiterals.FIND_LATE_OPENING_BALANCE_POSTING;
  static final String FIND_OPENING_BALANCE_NOMINAL_ACCOUNT =
      SqlitePostingSqlLiterals.FIND_OPENING_BALANCE_NOMINAL_ACCOUNT;
  static final String FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT =
      SqlitePostingSqlLiterals.FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT;
  static final String FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD =
      SqlitePostingSqlLiterals.FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD;
  static final String FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING =
      SqlitePostingSqlLiterals.FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING;
  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK =
      SqlitePostingSqlLiterals.FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK;
  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT =
      SqlitePostingSqlLiterals.FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT;
  static final String LOAD_PERSISTED_MONEY_AUDIT_ROWS =
      SqlitePostingSqlLiterals.LOAD_PERSISTED_MONEY_AUDIT_ROWS;
  static final String FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY =
      SqlitePostingSqlLiterals.FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY;
  static final String INSERT_BOOK_META_VALUE = SqlitePostingSqlLiterals.INSERT_BOOK_META_VALUE;
  static final String INSERT_BOOK_IDENTITY = SqlitePostingSqlLiterals.INSERT_BOOK_IDENTITY;
  static final String UPSERT_ACCOUNT = SqlitePostingSqlLiterals.UPSERT_ACCOUNT;

  static String listAccounts() {
    return SqlitePostingSqlQueryBuilder.listAccounts();
  }

  static String findAccountsByCodeCount(int accountCount) {
    return SqlitePostingSqlQueryBuilder.findAccountsByCodeCount(accountCount);
  }

  static String listPostings(PostingHistoryQuery query) {
    return SqlitePostingSqlQueryBuilder.listPostings(query);
  }

  static String loadAccountLinesForBalance(AccountBalanceCriteria query) {
    return SqlitePostingSqlQueryBuilder.loadAccountLinesForBalance(query);
  }

  static String loadTrialBalanceLines(TrialBalanceCriteria query) {
    return SqlitePostingSqlQueryBuilder.loadTrialBalanceLines(query);
  }

  static String loadAccountTotals(TrialBalanceCriteria query) {
    return SqlitePostingSqlQueryBuilder.loadAccountTotals(query);
  }

  static String loadAccountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    return SqlitePostingSqlQueryBuilder.loadAccountTotals(effectiveDateRange, postingCoverage);
  }

  static String loadPeriodSummaryLines(PeriodSummaryCriteria query) {
    return SqlitePostingSqlQueryBuilder.loadPeriodSummaryLines(query);
  }

  static String listPostingsForAccountLedger(AccountLedgerCriteria query) {
    return SqlitePostingSqlQueryBuilder.listPostingsForAccountLedger(query);
  }

  private SqlitePostingSql() {}
}
