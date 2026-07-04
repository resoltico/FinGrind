package dev.erst.fingrind.sqlite;

/** Reporting-period-close SQL for the SQLite posting adapter. */
final class SqliteReportingPeriodCloseSql {
  static final String INSERT_PERIOD_RESULT_TRANSFER =
      """
      insert into interim_result_sweep (
          effective_date_from,
          effective_date_to,
          result_holding_account_code,
          swept_at
      ) values (?, ?, ?, ?)
      returning interim_result_sweep_order
      """;

  static final String INSERT_PERIOD_RESULT_TRANSFER_TOTAL =
      """
      insert into interim_result_sweep_total (
          interim_result_sweep_order,
          currency_code,
          debit_total_minor,
          credit_total_minor
      ) values (?, ?, ?, ?)
      """;

  static final String INSERT_PERIOD_RESULT_TRANSFER_POSTING =
      """
      insert into interim_result_sweep_posting (
          interim_result_sweep_order,
          posting_id
      ) values (?, ?)
      """;

  static final String FIND_CLOSED_THROUGH_EFFECTIVE_DATE =
      """
      select effective_date_to
      from interim_result_sweep
      order by interim_result_sweep_order desc
      limit 1
      """;

  static final String FIND_LATEST_CLOSED_THROUGH_WITHIN_PERIOD =
      """
      select effective_date_to
      from interim_result_sweep
      where effective_date_from >= ? and effective_date_to <= ?
      order by effective_date_to desc, interim_result_sweep_order desc
      limit 1
      """;

  static final String INSERT_FISCAL_YEAR_CLOSE =
      """
      insert into fiscal_year_close (
          effective_date_from,
          effective_date_to,
          capital_account_code,
          result_holding_account_code,
          retained_accumulated_account_code,
          closed_at
      ) values (?, ?, ?, ?, ?, ?)
      returning fiscal_year_close_order
      """;

  static final String FIND_FISCAL_YEAR_CLOSE_BY_PERIOD =
      """
      select
          fiscal_year_close_order,
          capital_account_code,
          result_holding_account_code,
          retained_accumulated_account_code,
          closed_at
      from fiscal_year_close
      where effective_date_from = ? and effective_date_to = ?
      """;

  static final String INSERT_FISCAL_YEAR_CLOSE_POSTING =
      """
      insert into fiscal_year_close_posting (
          fiscal_year_close_order,
          posting_id
      ) values (?, ?)
      """;

  static final String FIND_FISCAL_YEAR_CLOSE_POSTING_IDS =
      """
      select posting_id
      from fiscal_year_close_posting
      where fiscal_year_close_order = ?
      order by rowid
      """;

  static final String FIND_EARLIEST_POSTING_EFFECTIVE_DATE =
      """
      select effective_date
      from posting_fact
      order by effective_date
      limit 1
      """;

  private SqliteReportingPeriodCloseSql() {}
}
