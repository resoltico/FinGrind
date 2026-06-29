package dev.erst.fingrind.sqlite;

/** Canonical SQLite SQL literals for declared tax registrations and applied-tax facts. */
final class SqliteTaxSql {
  static final String BASE_TAX_REGISTRATION_SELECT =
      """
      select
          tax_registration_id,
          tax_registration_name,
          jurisdiction,
          registration_number,
          payable_account_code,
          recoverable_account_code,
          obligation_frequency,
          due_days_after_period_end,
          declared_at
      from tax_registration
      """;

  static final String FIND_TAX_REGISTRATION_BY_ID =
      BASE_TAX_REGISTRATION_SELECT + " where tax_registration_id = ? limit 1";

  static final String LOAD_ALL_TAX_REGISTRATIONS =
      BASE_TAX_REGISTRATION_SELECT + " order by tax_registration_id";

  static final String LIST_TAX_REGISTRATIONS =
      BASE_TAX_REGISTRATION_SELECT
          + """
             where (? is null or tax_registration_id > ?)
             order by tax_registration_id
             limit ?
             """;

  static final String LOAD_TAX_CODES_FOR_REGISTRATION =
      """
      select
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind
      from tax_registration_code
      where tax_registration_id = ?
      order by tax_code
      """;

  static final String UPSERT_TAX_REGISTRATION =
      """
      insert into tax_registration (
          tax_registration_id,
          tax_registration_name,
          jurisdiction,
          registration_number,
          payable_account_code,
          recoverable_account_code,
          obligation_frequency,
          due_days_after_period_end,
          declared_at
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (tax_registration_id) do update set
          tax_registration_name = excluded.tax_registration_name,
          jurisdiction = excluded.jurisdiction,
          registration_number = excluded.registration_number,
          payable_account_code = excluded.payable_account_code,
          recoverable_account_code = excluded.recoverable_account_code,
          obligation_frequency = excluded.obligation_frequency,
          due_days_after_period_end = excluded.due_days_after_period_end
      """;

  static final String DELETE_TAX_CODES_FOR_REGISTRATION =
      "delete from tax_registration_code where tax_registration_id = ?";

  static final String INSERT_TAX_REGISTRATION_CODE =
      """
      insert into tax_registration_code (
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind
      ) values (?, ?, ?, ?, ?, ?)
      """;

  static final String LOAD_POSTING_APPLIED_TAX =
      """
      select
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      from posting_applied_tax
      where posting_id = ?
      limit 1
      """;

  static final String INSERT_POSTING_APPLIED_TAX =
      """
      insert into posting_applied_tax (
          posting_id,
          tax_registration_id,
          tax_code,
          tax_code_name,
          rate_parts_per_million_of_whole,
          inclusion_mode,
          application_kind,
          currency_code,
          taxable_amount_minor,
          tax_amount_minor,
          gross_amount_minor,
          tax_account_code
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  private SqliteTaxSql() {}
}
