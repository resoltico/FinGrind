package dev.erst.fingrind.sqlite;

/** Canonical SQLite SQL literals for the durable accrual cut-off aggregate and applications. */
final class SqliteAccrualCutoffSql {
  static final String BASE_CUTOFF_SELECT =
      """
      select
          accrual_cutoff.accrual_cutoff_id,
          accrual_cutoff.kind,
          accrual_cutoff.originated_on,
          accrual_cutoff.cutoff_account_code,
          accrual_cutoff.recognition_account_code,
          accrual_cutoff.amount_currency_code,
          accrual_cutoff.original_amount_minor,
          accrual_cutoff.recognition_start_date,
          accrual_cutoff.recognition_end_date,
          coalesce(sum(accrual_cutoff_application.amount_minor), 0),
          max(accrual_cutoff_application.effective_date)
      from accrual_cutoff
      left join accrual_cutoff_application
          on accrual_cutoff_application.accrual_cutoff_id = accrual_cutoff.accrual_cutoff_id
      """;

  static final String FIND_CUTOFF_BY_ID =
      BASE_CUTOFF_SELECT
          + """
            where accrual_cutoff.accrual_cutoff_id = ?
            group by
                accrual_cutoff.accrual_cutoff_id,
                accrual_cutoff.kind,
                accrual_cutoff.originated_on,
                accrual_cutoff.cutoff_account_code,
                accrual_cutoff.recognition_account_code,
                accrual_cutoff.amount_currency_code,
                accrual_cutoff.original_amount_minor,
                accrual_cutoff.recognition_start_date,
                accrual_cutoff.recognition_end_date
            limit 1
            """;

  static final String FIND_CUTOFF_BY_ORIGIN_POSTING_ID =
      BASE_CUTOFF_SELECT
          + """
            where accrual_cutoff.origin_posting_id = ?
            group by
                accrual_cutoff.accrual_cutoff_id,
                accrual_cutoff.kind,
                accrual_cutoff.originated_on,
                accrual_cutoff.cutoff_account_code,
                accrual_cutoff.recognition_account_code,
                accrual_cutoff.amount_currency_code,
                accrual_cutoff.original_amount_minor,
                accrual_cutoff.recognition_start_date,
                accrual_cutoff.recognition_end_date
            limit 1
            """;

  static final String FIND_APPLICATION_CONTEXT_BY_POSTING_ID =
      """
      select accrual_cutoff_id, application_kind
      from accrual_cutoff_application
      where application_posting_id = ?
      limit 1
      """;

  static final String FIND_APPLICATION_REVERSAL_INPUT_BY_POSTING_ID =
      """
      select accrual_cutoff_id, amount_currency_code, amount_minor
      from accrual_cutoff_application
      where application_posting_id = ?
        and application_kind in ('RECOGNITION', 'SETTLEMENT')
      limit 1
      """;

  static final String LIST_CUTOFFS =
      BASE_CUTOFF_SELECT
          + """
            group by
                accrual_cutoff.accrual_cutoff_id,
                accrual_cutoff.kind,
                accrual_cutoff.originated_on,
                accrual_cutoff.cutoff_account_code,
                accrual_cutoff.recognition_account_code,
                accrual_cutoff.amount_currency_code,
                accrual_cutoff.original_amount_minor,
                accrual_cutoff.recognition_start_date,
                accrual_cutoff.recognition_end_date
            order by accrual_cutoff.originated_on, accrual_cutoff.accrual_cutoff_id
            """;

  static final String LIST_CUTOFFS_AS_OF =
      """
      select
          accrual_cutoff.accrual_cutoff_id,
          accrual_cutoff.kind,
          accrual_cutoff.originated_on,
          accrual_cutoff.cutoff_account_code,
          accrual_cutoff.recognition_account_code,
          accrual_cutoff.amount_currency_code,
          accrual_cutoff.original_amount_minor,
          accrual_cutoff.recognition_start_date,
          accrual_cutoff.recognition_end_date,
          coalesce(sum(accrual_cutoff_application.amount_minor), 0),
          max(accrual_cutoff_application.effective_date)
      from accrual_cutoff
      left join accrual_cutoff_application
          on accrual_cutoff_application.accrual_cutoff_id = accrual_cutoff.accrual_cutoff_id
          and accrual_cutoff_application.effective_date <= ?
      where accrual_cutoff.originated_on <= ?
      group by
          accrual_cutoff.accrual_cutoff_id,
          accrual_cutoff.kind,
          accrual_cutoff.originated_on,
          accrual_cutoff.cutoff_account_code,
          accrual_cutoff.recognition_account_code,
          accrual_cutoff.amount_currency_code,
          accrual_cutoff.original_amount_minor,
          accrual_cutoff.recognition_start_date,
          accrual_cutoff.recognition_end_date
      order by accrual_cutoff.originated_on, accrual_cutoff.accrual_cutoff_id
      """;

  static final String INSERT_CUTOFF =
      """
      insert into accrual_cutoff (
          accrual_cutoff_id,
          kind,
          origin_posting_id,
          originated_on,
          cutoff_account_code,
          recognition_account_code,
          amount_currency_code,
          original_amount_minor,
          recognition_start_date,
          recognition_end_date
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_APPLICATION =
      """
      insert into accrual_cutoff_application (
          application_posting_id,
          accrual_cutoff_id,
          application_kind,
          effective_date,
          amount_currency_code,
          amount_minor
      ) values (?, ?, ?, ?, ?, ?)
      """;

  private SqliteAccrualCutoffSql() {}
}
