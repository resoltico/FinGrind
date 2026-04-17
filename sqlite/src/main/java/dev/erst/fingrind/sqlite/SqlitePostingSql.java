package dev.erst.fingrind.sqlite;

/** Canonical SQL statements for the SQLite posting adapter. */
final class SqlitePostingSql {
  static final String INITIALIZED_AT_META_KEY = "initialized_at";

  static final int COL_POSTING_ID = 0;
  static final int COL_EFFECTIVE_DATE = 1;
  static final int COL_RECORDED_AT = 2;
  static final int COL_ACTOR_ID = 3;
  static final int COL_ACTOR_TYPE = 4;
  static final int COL_COMMAND_ID = 5;
  static final int COL_IDEMPOTENCY_KEY = 6;
  static final int COL_CAUSATION_ID = 7;
  static final int COL_CORRELATION_ID = 8;
  static final int COL_REASON = 9;
  static final int COL_SOURCE_CHANNEL = 10;
  static final int COL_PRIOR_POSTING_ID = 11;

  static final int COL_LINE_ACCOUNT_CODE = 0;
  static final int COL_LINE_ENTRY_SIDE = 1;
  static final int COL_LINE_CURRENCY_CODE = 2;
  static final int COL_LINE_AMOUNT = 3;

  static final int COL_ACCOUNT_CODE = 0;
  static final int COL_ACCOUNT_NAME = 1;
  static final int COL_ACCOUNT_NORMAL_BALANCE = 2;
  static final int COL_ACCOUNT_ACTIVE = 3;
  static final int COL_ACCOUNT_DECLARED_AT = 4;

  private static final String BASE_POSTING_SELECT =
      """
      select
          posting_id,
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
          normal_balance,
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
      select account_code, entry_side, currency_code, amount
      from journal_line
      where posting_id = ?
      order by line_order
      """;

  static final String LOAD_ACCOUNT_LINES_FOR_BALANCE =
      """
      select journal_line.entry_side, journal_line.currency_code, journal_line.amount
      from journal_line
      join posting_fact on posting_fact.posting_id = journal_line.posting_id
      where journal_line.account_code = ?
      """;

  static final String INSERT_POSTING_FACT =
      """
      insert into posting_fact (
          posting_id,
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
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_JOURNAL_LINE =
      """
      insert into journal_line (
          posting_id,
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount
      ) values (?, ?, ?, ?, ?, ?)
      """;

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
          normal_balance,
          active,
          declared_at
      ) values (?, ?, ?, ?, ?)
      on conflict (account_code) do update set
          account_name = excluded.account_name,
          normal_balance = excluded.normal_balance,
          active = excluded.active,
          declared_at = excluded.declared_at
      """;

  static String listAccounts() {
    return BASE_ACCOUNT_SELECT + " order by account_code limit ? offset ?";
  }

  static String listPostings(
      boolean filterAccount, boolean filterEffectiveDateFrom, boolean filterEffectiveDateTo) {
    StringBuilder sql =
        new StringBuilder(BASE_POSTING_SELECT.length() + 256)
            .append(BASE_POSTING_SELECT)
            .append(" where 1 = 1");
    if (filterAccount) {
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
    if (filterEffectiveDateFrom) {
      sql.append(" and effective_date >= ?");
    }
    if (filterEffectiveDateTo) {
      sql.append(" and effective_date <= ?");
    }
    sql.append(" order by effective_date desc, recorded_at desc, posting_id desc limit ? offset ?");
    return sql.toString();
  }

  static String loadAccountLinesForBalance(
      boolean filterEffectiveDateFrom, boolean filterEffectiveDateTo) {
    StringBuilder sql =
        new StringBuilder(LOAD_ACCOUNT_LINES_FOR_BALANCE.length() + 96)
            .append(LOAD_ACCOUNT_LINES_FOR_BALANCE);
    if (filterEffectiveDateFrom) {
      sql.append(" and posting_fact.effective_date >= ?");
    }
    if (filterEffectiveDateTo) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by posting_fact.effective_date, posting_fact.recorded_at, journal_line.line_order");
    return sql.toString();
  }

  private SqlitePostingSql() {}
}
