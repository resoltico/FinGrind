package dev.erst.fingrind.sqlite;

/** SQL owned by the SQLite adapter for immutable Latvian monthly-payroll run facts. */
final class SqliteLatvianPayrollSql {
  static final String BASE_RUN_SELECT =
      """
      select
          payroll_run_id,
          employee_reference,
          payroll_month,
          effective_date,
          wage_expense_account_code,
          employer_social_expense_account_code,
          net_wages_payable_account_code,
          employee_social_payable_account_code,
          employer_social_payable_account_code,
          personal_income_tax_payable_account_code,
          currency_code,
          gross_wages_minor,
          employee_social_contribution_minor,
          employer_social_contribution_minor,
          non_taxable_minimum_minor,
          personal_income_tax_minor,
          net_wages_minor,
          origin_posting_id,
          reversal_posting_id
      from latvian_payroll_run
      left join latvian_payroll_run_reversal
        using (payroll_run_id)
      """;

  static final String INSERT_RUN =
      """
      insert into latvian_payroll_run (
          payroll_run_id,
          origin_posting_id,
          employee_reference,
          payroll_month,
          effective_date,
          wage_expense_account_code,
          employer_social_expense_account_code,
          net_wages_payable_account_code,
          employee_social_payable_account_code,
          employer_social_payable_account_code,
          personal_income_tax_payable_account_code,
          currency_code,
          gross_wages_minor,
          employee_social_contribution_minor,
          employer_social_contribution_minor,
          non_taxable_minimum_minor,
          personal_income_tax_minor,
          net_wages_minor
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_REVERSAL =
      """
      insert into latvian_payroll_run_reversal (
          reversal_posting_id,
          payroll_run_id
      ) values (?, ?)
      """;

  static final String FIND_RUN_BY_ID = BASE_RUN_SELECT + " where payroll_run_id = ? limit 1";

  static final String FIND_RUN_BY_ORIGIN_POSTING_ID =
      BASE_RUN_SELECT + " where origin_posting_id = ? limit 1";

  static final String FIND_ACTIVE_RUN_BY_EMPLOYEE_MONTH =
      BASE_RUN_SELECT
          + """
             where employee_reference = ?
               and payroll_month = ?
               and reversal_posting_id is null
             limit 1
             """;

  static final String LIST_RUNS = BASE_RUN_SELECT + " order by payroll_month, payroll_run_id";

  static final String BASE_SETTLEMENT_SELECT =
      """
      select
          settlement_kind,
          payroll_run_id,
          origin_posting_id,
          effective_date,
          cash_account_code,
          reversal_posting_id
      from latvian_payroll_settlement
      left join latvian_payroll_settlement_reversal
        using (origin_posting_id)
      """;

  static final String INSERT_SETTLEMENT =
      """
      insert into latvian_payroll_settlement (
          origin_posting_id,
          payroll_run_id,
          settlement_kind,
          effective_date,
          cash_account_code
      ) values (?, ?, ?, ?, ?)
      """;

  static final String INSERT_SETTLEMENT_REVERSAL =
      """
      insert into latvian_payroll_settlement_reversal (
          reversal_posting_id,
          origin_posting_id
      ) values (?, ?)
      """;

  static final String FIND_ACTIVE_SETTLEMENT =
      BASE_SETTLEMENT_SELECT
          + """
            where payroll_run_id = ?
              and settlement_kind = ?
              and reversal_posting_id is null
            limit 1
            """;

  static final String FIND_SETTLEMENT_BY_ORIGIN_POSTING_ID =
      BASE_SETTLEMENT_SELECT + " where origin_posting_id = ? limit 1";

  static final String LIST_SETTLEMENTS =
      BASE_SETTLEMENT_SELECT
          + " order by payroll_run_id, settlement_kind, effective_date, origin_posting_id";

  private SqliteLatvianPayrollSql() {}
}
