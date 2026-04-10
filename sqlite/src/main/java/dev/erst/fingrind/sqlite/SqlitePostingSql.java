package dev.erst.fingrind.sqlite;

/** Canonical SQL statements for the SQLite posting adapter. */
final class SqlitePostingSql {
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
          correction_kind,
          prior_posting_id
      from posting_fact
      """;

  static final String FIND_POSTING_BY_IDEMPOTENCY =
      BASE_POSTING_SELECT + " where idempotency_key = ? limit 1";

  static final String FIND_POSTING_BY_ID = BASE_POSTING_SELECT + " where posting_id = ? limit 1";

  static final String FIND_REVERSAL_FOR =
      BASE_POSTING_SELECT + " where correction_kind = 'REVERSAL' and prior_posting_id = ? limit 1";

  static final String EXISTS_POSTING_BY_IDEMPOTENCY =
      "select 1 from posting_fact where idempotency_key = ? limit 1";

  static final String EXISTS_REVERSAL_FOR =
      "select 1 from posting_fact where correction_kind = 'REVERSAL' and prior_posting_id = ? limit 1";

  static final String LOAD_LINES =
      """
      select account_code, entry_side, currency_code, amount
      from journal_line
      where posting_id = ?
      order by line_order
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
          correction_kind,
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
          amount
      ) values (?, ?, ?, ?, ?, ?)
      """;

  private SqlitePostingSql() {}
}
