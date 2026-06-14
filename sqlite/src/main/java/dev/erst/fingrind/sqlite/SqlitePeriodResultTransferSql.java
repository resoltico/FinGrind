package dev.erst.fingrind.sqlite;

/** Period-result-transfer SQL for the SQLite posting adapter. */
final class SqlitePeriodResultTransferSql {
  static final String INSERT_PERIOD_RESULT_TRANSFER =
      """
      insert into period_result_transfer (
          effective_date_from,
          effective_date_to,
          closing_equity_account_code,
          closed_at
      ) values (?, ?, ?, ?)
      returning period_result_transfer_order
      """;

  static final String INSERT_PERIOD_RESULT_TRANSFER_TOTAL =
      """
      insert into period_result_transfer_total (
          period_result_transfer_order,
          currency_code,
          debit_total_minor,
          credit_total_minor
      ) values (?, ?, ?, ?)
      """;

  static final String INSERT_PERIOD_RESULT_TRANSFER_POSTING =
      """
      insert into period_result_transfer_posting (
          period_result_transfer_order,
          posting_id
      ) values (?, ?)
      """;

  static final String FIND_CLOSED_THROUGH_EFFECTIVE_DATE =
      """
      select effective_date_to
      from period_result_transfer
      order by period_result_transfer_order desc
      limit 1
      """;

  static final String FIND_EARLIEST_POSTING_EFFECTIVE_DATE =
      """
      select effective_date
      from posting_fact
      order by effective_date
      limit 1
      """;

  private SqlitePeriodResultTransferSql() {}
}
