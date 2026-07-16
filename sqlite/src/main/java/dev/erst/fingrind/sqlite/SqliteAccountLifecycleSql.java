package dev.erst.fingrind.sqlite;

/** Canonical SQLite statements for Account Registry lifecycle checks and mutations. */
final class SqliteAccountLifecycleSql {
  static final String ACCOUNT_HAS_POSTINGS =
      "select 1 from journal_line where account_code = ? limit 1";
  static final String ACCOUNT_HAS_TAX_REGISTRATIONS =
      """
      select 1
      from tax_registration
      where payable_account_code = ? or recoverable_account_code = ?
      limit 1
      """;
  static final String ACCOUNT_HAS_CHILDREN =
      "select 1 from account where parent_account_code = ? limit 1";
  static final String ACCOUNT_HAS_NON_ZERO_BALANCE =
      """
      select 1
      from journal_line
      where account_code = ?
      group by currency_code
      having sum(case entry_side when 'DEBIT' then amount_minor else -amount_minor end) <> 0
      limit 1
      """;
  static final String UPDATE_ACCOUNT_DEFINITION =
      """
      update account
      set
          account_name = ?,
          account_type = ?,
          account_node_kind = ?,
          parent_account_code = ?,
          financial_position_line_classification = ?,
          cash_flow_asset_classification = ?,
          profit_and_loss_line_classification = ?,
          unit_of_measure = ?,
          quantity_scale = ?
      where account_code = ?
      """;
  static final String RETIRE_ACCOUNT = "update account set active = 0 where account_code = ?";

  private SqliteAccountLifecycleSql() {}
}
