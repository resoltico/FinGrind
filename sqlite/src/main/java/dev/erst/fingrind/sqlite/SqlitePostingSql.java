package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import java.util.Collections;

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
  static final String FIND_ENTITY_PROFILE = SqlitePostingSqlLiterals.FIND_ENTITY_PROFILE;
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
  static final String INSERT_ENTITY_PROFILE = SqlitePostingSqlLiterals.INSERT_ENTITY_PROFILE;
  static final String UPSERT_ACCOUNT = SqlitePostingSqlLiterals.UPSERT_ACCOUNT;

  static String listAccounts() {
    return SqlitePostingSqlLiterals.BASE_ACCOUNT_SELECT
        + " where (? is null or account_code > ?) order by account_code limit ?";
  }

  static String findAccountsByCodeCount(int accountCount) {
    if (accountCount < 1) {
      throw new IllegalArgumentException("Account lookup count must be at least one.");
    }
    return SqlitePostingSqlLiterals.BASE_ACCOUNT_SELECT
        + " where account_code in ("
        + String.join(", ", Collections.nCopies(accountCount, "?"))
        + ")";
  }

  static String listPostings(PostingHistoryQuery query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_POSTING_SELECT.length() + 256)
            .append(SqlitePostingSqlLiterals.BASE_POSTING_SELECT)
            .append(" where 1 = 1");
    if (query.accountCode().isPresent()) {
      sql.append(
          """
           and exists (
               select 1
               from journal_line
               where journal_line.posting_id = posting_fact.posting_id
                 and journal_line.account_code = ?
           )
          """);
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and effective_date <= ?");
    }
    if (query.cursor().isPresent()) {
      sql.append(
          """
           and (
               effective_date < ?
               or (effective_date = ? and recorded_at < ?)
               or (effective_date = ? and recorded_at = ? and posting_id < ?)
           )
          """);
    }
    sql.append(" order by effective_date desc, recorded_at desc, posting_id desc limit ?");
    return sql.toString();
  }

  static String loadAccountLinesForBalance(AccountBalanceCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.LOAD_ACCOUNT_LINES_FOR_BALANCE.length() + 96)
            .append(SqlitePostingSqlLiterals.LOAD_ACCOUNT_LINES_FOR_BALANCE);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and posting_fact.effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by posting_fact.effective_date, posting_fact.recorded_at, journal_line.line_order");
    return sql.toString();
  }

  static String loadTrialBalanceLines(TrialBalanceCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 96)
            .append(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT)
            .append(" where 1 = 1");
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'");
    }
    if (query.effectiveDateAsOf().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by account.account_code, journal_line.currency_code, posting_fact.effective_date, posting_fact.recorded_at, posting_fact.posting_id");
    return sql.toString();
  }

  static String loadAccountTotals(TrialBalanceCriteria query) {
    return loadAccountTotals(
        query
            .effectiveDateAsOf()
            .map(EffectiveDateRange::to)
            .orElseGet(EffectiveDateRange::unbounded),
        query.postingCoverage());
  }

  static String loadAccountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 256)
            .append(
                """
                select
                    account.account_code,
                    account.account_name,
                    account.account_type,
                    account.account_role,
                    account.account_node_kind,
                    account.parent_account_code,
                    account.financial_position_line_classification,
                    account.profit_and_loss_line_classification,
                    account.active,
                    account.declared_at,
                    journal_line.currency_code,
                    sum(case when journal_line.entry_side = 'DEBIT' then journal_line.amount_minor else 0 end) as debit_minor,
                    sum(case when journal_line.entry_side = 'CREDIT' then journal_line.amount_minor else 0 end) as credit_minor
                from journal_line
                join posting_fact on posting_fact.posting_id = journal_line.posting_id
                join account on account.account_code = journal_line.account_code
                where 1 = 1
                """);
    if (postingCoverage.isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'");
    }
    if (effectiveDateRange.effectiveDateFrom().isPresent()) {
      sql.append(" and posting_fact.effective_date >= ?");
    }
    if (effectiveDateRange.effectiveDateTo().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        """
         group by
             account.account_code,
             account.account_name,
             account.account_type,
             account.account_role,
             account.account_node_kind,
             account.parent_account_code,
             account.financial_position_line_classification,
             account.profit_and_loss_line_classification,
             account.active,
             account.declared_at,
             journal_line.currency_code
         order by account.account_code, journal_line.currency_code
        """);
    return sql.toString();
  }

  static String loadPeriodSummaryLines(PeriodSummaryCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 128)
            .append(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT)
            .append(
                """
                 where posting_fact.effective_date >= ?
                   and posting_fact.effective_date <= ?
                """);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    sql.append(
        """
         order by posting_fact.effective_date, posting_fact.recorded_at, posting_fact.posting_id, journal_line.line_order
        """);
    return sql.toString();
  }

  static String listPostingsForAccountLedger(AccountLedgerCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_POSTING_SELECT.length() + 192)
            .append(SqlitePostingSqlLiterals.BASE_POSTING_SELECT)
            .append(
                """
                 where exists (
                     select 1
                     from journal_line
                     where journal_line.posting_id = posting_fact.posting_id
                       and journal_line.account_code = ?
                 )
                """);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and effective_date <= ?");
    }
    sql.append(" order by effective_date, recorded_at, posting_id");
    return sql.toString();
  }

  private SqlitePostingSql() {}
}
