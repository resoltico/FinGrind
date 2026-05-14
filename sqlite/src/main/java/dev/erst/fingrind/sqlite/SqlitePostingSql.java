package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import java.util.Collections;
import java.util.List;

/** Canonical SQL statements for the SQLite posting adapter. */
final class SqlitePostingSql {
  static final String INITIALIZED_AT_META_KEY = "initialized_at";
  static final String BOOK_ENTITY_NAME_META_KEY = "entity_name";
  static final String BOOK_FUNCTIONAL_CURRENCY_META_KEY = "functional_currency_code";
  static final String BOOK_FISCAL_YEAR_START_META_KEY = "fiscal_year_start";
  static final String SCHEMA_FINGERPRINT_META_KEY = "schema_fingerprint_sha256";

  static final int COL_POSTING_ID = 0;
  static final int COL_POSTING_KIND = 1;
  static final int COL_EFFECTIVE_DATE = 2;
  static final int COL_RECORDED_AT = 3;
  static final int COL_ACTOR_ID = 4;
  static final int COL_ACTOR_TYPE = 5;
  static final int COL_COMMAND_ID = 6;
  static final int COL_IDEMPOTENCY_KEY = 7;
  static final int COL_CAUSATION_ID = 8;
  static final int COL_CORRELATION_ID = 9;
  static final int COL_REASON = 10;
  static final int COL_SOURCE_CHANNEL = 11;
  static final int COL_PRIOR_POSTING_ID = 12;

  static final int COL_LINE_ACCOUNT_CODE = 0;
  static final int COL_LINE_ENTRY_SIDE = 1;
  static final int COL_LINE_CURRENCY_CODE = 2;
  static final int COL_LINE_AMOUNT_MINOR = 3;

  static final int COL_ACCOUNT_CODE = 0;
  static final int COL_ACCOUNT_NAME = 1;
  static final int COL_ACCOUNT_TYPE = 2;
  static final int COL_ACCOUNT_ROLE = 3;
  static final int COL_ACCOUNT_ACTIVE = 4;
  static final int COL_ACCOUNT_DECLARED_AT = 5;
  static final int COL_REPORT_POSTING_ID = 6;
  static final int COL_REPORT_ENTRY_SIDE = 7;
  static final int COL_REPORT_CURRENCY_CODE = 8;
  static final int COL_REPORT_AMOUNT_MINOR = 9;
  static final int COL_TOTAL_CURRENCY_CODE = 6;
  static final int COL_TOTAL_DEBIT_MINOR = 7;
  static final int COL_TOTAL_CREDIT_MINOR = 8;

  private static final String BASE_POSTING_SELECT =
      """
      select
          posting_id,
          posting_kind,
          effective_date,
          recorded_at,
          actor_id,
          actor_type,
          command_id,
          idempotency_key,
          causation_id,
          correlation_id,
          reason,
          source_channel,
          prior_posting_id
      from posting_fact
      """;

  private static final String BASE_ACCOUNT_SELECT =
      """
      select
          account_code,
          account_name,
          account_type,
          account_role,
          active,
          declared_at
      from account
      """;

  static final String USER_SCHEMA_EXISTS =
      """
      select 1
      from sqlite_schema
      where type in ('table', 'index', 'trigger', 'view')
        and name not like 'sqlite_%'
      limit 1
      """;

  static final String TABLE_EXISTS =
      """
      select 1
      from sqlite_schema
      where type = 'table'
        and name = ?
      limit 1
      """;

  static final String BOOK_INITIALIZED_EXISTS =
      """
      select 1
      from book_meta
      where key = ?
      limit 1
      """;

  static final String FIND_BOOK_INITIALIZED_AT =
      """
      select value
      from book_meta
      where key = ?
      limit 1
      """;

  static final String FIND_BOOK_META_VALUE =
      """
      select value
      from book_meta
      where key = ?
      limit 1
      """;

  static final String PRAGMA_INTEGRITY_CHECK = "pragma integrity_check";
  static final String PRAGMA_FOREIGN_KEY_CHECK = "pragma foreign_key_check";

  static final List<String> CANONICAL_SCHEMA_OBJECT_NAMES =
      List.of(
          "book_meta",
          "account",
          "posting_fact",
          "journal_line",
          "period_close",
          "period_close_posting",
          "audit_event",
          "posting_fact_by_prior_posting_id",
          "posting_fact_by_effective_recorded_posting",
          "journal_line_by_account_code",
          "audit_event_by_recorded_at",
          "period_close_by_effective_date_to",
          "period_close_posting_by_posting_id",
          "posting_fact_one_reversal_per_target",
          "posting_fact_reject_update",
          "posting_fact_reject_delete",
          "journal_line_reject_update",
          "journal_line_reject_delete",
          "period_close_reject_update",
          "period_close_reject_delete",
          "period_close_posting_reject_update",
          "period_close_posting_reject_delete",
          "audit_event_reject_update",
          "audit_event_reject_delete");

  static final int EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT = CANONICAL_SCHEMA_OBJECT_NAMES.size();

  static final String LOAD_CANONICAL_SCHEMA_OBJECTS =
      """
      select type, name, ifnull(sql, '')
      from sqlite_schema
      where type in ('table', 'index', 'trigger')
        and name in (
            %s
        )
      order by type, name
      """
          .formatted("'" + String.join("',\n            '", CANONICAL_SCHEMA_OBJECT_NAMES) + "'");

  static final String FIND_ACCOUNT_BY_CODE =
      BASE_ACCOUNT_SELECT + " where account_code = ? limit 1";

  static final String FIND_POSTING_BY_IDEMPOTENCY =
      BASE_POSTING_SELECT + " where idempotency_key = ? limit 1";

  static final String FIND_POSTING_BY_ID = BASE_POSTING_SELECT + " where posting_id = ? limit 1";

  static final String FIND_REVERSAL_FOR =
      BASE_POSTING_SELECT + " where prior_posting_id = ? limit 1";

  static final String EXISTS_POSTING_BY_IDEMPOTENCY =
      "select 1 from posting_fact where idempotency_key = ? limit 1";

  static final String EXISTS_REVERSAL_FOR =
      "select 1 from posting_fact where prior_posting_id = ? limit 1";

  static final String LOAD_LINES =
      """
      select account_code, entry_side, currency_code, amount_minor
      from journal_line
      where posting_id = ?
      order by line_order
      """;

  static final String LOAD_ACCOUNT_LINES_FOR_BALANCE =
      """
      select
          journal_line.entry_side,
          journal_line.currency_code,
          journal_line.amount_minor
      from journal_line
      join posting_fact on posting_fact.posting_id = journal_line.posting_id
      where journal_line.account_code = ?
      """;

  private static final String BASE_REPORT_LINE_SELECT =
      """
      select
          account.account_code,
          account.account_name,
          account.account_type,
          account.account_role,
          account.active,
          account.declared_at,
          posting_fact.posting_id,
          journal_line.entry_side,
          journal_line.currency_code,
          journal_line.amount_minor
      from journal_line
      join posting_fact on posting_fact.posting_id = journal_line.posting_id
      join account on account.account_code = journal_line.account_code
      """;

  static final String INSERT_POSTING_FACT =
      """
      insert into posting_fact (
          posting_id,
          posting_kind,
          effective_date,
          recorded_at,
          actor_id,
          actor_type,
          command_id,
          idempotency_key,
          causation_id,
          correlation_id,
          reason,
          source_channel,
          prior_posting_id
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_JOURNAL_LINE =
      """
      insert into journal_line (
          posting_id,
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      ) values (?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_AUDIT_EVENT =
      """
      insert into audit_event (
          recorded_at,
          event_kind,
          account_code,
          posting_id,
          period_close_order
      ) values (?, ?, ?, ?, ?)
      """;

  static final String INSERT_PERIOD_CLOSE =
      """
      insert into period_close (
          effective_date_from,
          effective_date_to,
          closed_at
      ) values (?, ?, ?)
      returning period_close_order
      """;

  static final String INSERT_PERIOD_CLOSE_POSTING =
      """
      insert into period_close_posting (
          period_close_order,
          posting_id
      ) values (?, ?)
      """;

  static final String FIND_CLOSED_THROUGH_EFFECTIVE_DATE =
      """
      select effective_date_to
      from period_close
      order by period_close_order desc
      limit 1
      """;

  static final String FIND_EARLIEST_POSTING_EFFECTIVE_DATE =
      """
      select effective_date
      from posting_fact
      order by effective_date
      limit 1
      """;

  static final String LOAD_ALL_ACCOUNTS = BASE_ACCOUNT_SELECT + " order by account_code";

  static final String LOAD_POSTINGS_IN_RANGE =
      BASE_POSTING_SELECT
          + """
             where (? is null or effective_date >= ?)
               and (? is null or effective_date <= ?)
             order by effective_date, recorded_at, posting_id
             """;

  static final String CREATE_PENDING_JOURNAL_LINE =
      """
      create temporary table if not exists pending_journal_line (
          line_order integer not null check (line_order >= 0),
          account_code text not null,
          entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
          currency_code text not null,
          amount_minor integer not null check (amount_minor > 0)
      ) strict
      """;

  static final String CLEAR_PENDING_JOURNAL_LINE = "delete from pending_journal_line";

  static final String INSERT_PENDING_JOURNAL_LINE =
      """
      insert into pending_journal_line (
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      ) values (?, ?, ?, ?, ?)
      """;

  static final String VALID_PENDING_JOURNAL_LINE =
      """
      select 1
      from (
          select
              count(*) as line_count,
              sum(case when entry_side = 'DEBIT' then 1 else 0 end) as debit_count,
              sum(case when entry_side = 'CREDIT' then 1 else 0 end) as credit_count,
              count(distinct currency_code) as currency_bucket_count,
              sum(case when entry_side = 'DEBIT' then amount_minor else -amount_minor end) as signed_minor_total
          from pending_journal_line
      )
      where line_count >= 2
        and debit_count >= 1
        and credit_count >= 1
        and currency_bucket_count = 1
        and signed_minor_total = 0
      limit 1
      """;

  static final String PERSIST_PENDING_JOURNAL_LINE =
      """
      insert into journal_line (
          posting_id,
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      )
      select ?, line_order, account_code, entry_side, currency_code, amount_minor
      from pending_journal_line
      order by line_order
      """;

  static final String FIND_UNBALANCED_POSTING =
      """
      select posting_id
      from journal_line
      group by posting_id
      having count(*) < 2
          or sum(case when entry_side = 'DEBIT' then 1 else 0 end) = 0
          or sum(case when entry_side = 'CREDIT' then 1 else 0 end) = 0
          or count(distinct currency_code) <> 1
          or sum(case when entry_side = 'DEBIT' then amount_minor else -amount_minor end) <> 0
      limit 1
      """;

  static final String FIND_POSTING_WITHOUT_JOURNAL_LINES =
      """
      select posting_fact.posting_id
      from posting_fact
      left join journal_line on journal_line.posting_id = posting_fact.posting_id
      group by posting_fact.posting_id
      having count(journal_line.line_order) = 0
      limit 1
      """;

  static final String LOAD_PERSISTED_MONEY_AUDIT_ROWS =
      """
      select currency_code, amount_minor
      from journal_line
      order by posting_id, line_order
      """;

  static final String FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY =
      """
      select journal_line.posting_id
      from journal_line
      join book_meta
        on book_meta.key = '%s'
      where journal_line.currency_code <> book_meta.value
      limit 1
      """
          .formatted(BOOK_FUNCTIONAL_CURRENCY_META_KEY);

  static final String INSERT_BOOK_INITIALIZED_AT =
      """
      insert into book_meta (key, value)
      values (?, ?)
      """;

  static final String UPSERT_ACCOUNT =
      """
      insert into account (
          account_code,
          account_name,
          account_type,
          account_role,
          active,
          declared_at
      ) values (?, ?, ?, ?, ?, ?)
      on conflict (account_code) do update set
          account_name = excluded.account_name,
          active = excluded.active,
          declared_at = excluded.declared_at
      """;

  static String listAccounts() {
    return BASE_ACCOUNT_SELECT
        + " where (? is null or account_code > ?) order by account_code limit ?";
  }

  static String findAccountsByCodeCount(int accountCount) {
    if (accountCount < 1) {
      throw new IllegalArgumentException("Account lookup count must be at least one.");
    }
    return BASE_ACCOUNT_SELECT
        + " where account_code in ("
        + String.join(", ", Collections.nCopies(accountCount, "?"))
        + ")";
  }

  static String listPostings(PostingHistoryQuery query) {
    StringBuilder sql =
        new StringBuilder(BASE_POSTING_SELECT.length() + 256)
            .append(BASE_POSTING_SELECT)
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
        new StringBuilder(LOAD_ACCOUNT_LINES_FOR_BALANCE.length() + 96)
            .append(LOAD_ACCOUNT_LINES_FOR_BALANCE);
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
        new StringBuilder(BASE_REPORT_LINE_SELECT.length() + 96)
            .append(BASE_REPORT_LINE_SELECT)
            .append(" where 1 = 1");
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_CLOSE'");
    }
    if (query.effectiveDateTo().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by account.account_code, journal_line.currency_code, posting_fact.effective_date, posting_fact.recorded_at, posting_fact.posting_id");
    return sql.toString();
  }

  static String loadAccountTotals(TrialBalanceCriteria query) {
    return loadAccountTotals(
        query
            .effectiveDateTo()
            .map(date -> EffectiveDateRange.of(null, date))
            .orElseGet(() -> EffectiveDateRange.of(null, null)),
        query.postingCoverage());
  }

  static String loadAccountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    StringBuilder sql =
        new StringBuilder(BASE_REPORT_LINE_SELECT.length() + 256)
            .append(
                """
                select
                    account.account_code,
                    account.account_name,
                    account.account_type,
                    account.account_role,
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
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_CLOSE'");
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
             account.active,
             account.declared_at,
             journal_line.currency_code
         order by account.account_code, journal_line.currency_code
        """);
    return sql.toString();
  }

  static String loadPeriodSummaryLines(PeriodSummaryCriteria query) {
    StringBuilder sql =
        new StringBuilder(BASE_REPORT_LINE_SELECT.length() + 128)
            .append(BASE_REPORT_LINE_SELECT)
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
        new StringBuilder(BASE_POSTING_SELECT.length() + 192)
            .append(BASE_POSTING_SELECT)
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
