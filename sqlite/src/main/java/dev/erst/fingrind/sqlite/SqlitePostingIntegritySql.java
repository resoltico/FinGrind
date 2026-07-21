package dev.erst.fingrind.sqlite;

/** Integrity, schema, and audit SQL for the SQLite posting adapter. */
final class SqlitePostingIntegritySql {
  static final String PRAGMA_INTEGRITY_CHECK = "pragma integrity_check";
  static final String PRAGMA_FOREIGN_KEY_CHECK = "pragma foreign_key_check";

  static final int EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT =
      SqliteCanonicalSchemaManifest.objectCount();

  static final String LOAD_CANONICAL_SCHEMA_OBJECTS =
      SqliteCanonicalSchemaManifest.loadObjectsQuery();

  static final String LOAD_NON_CANONICAL_SCHEMA_OBJECTS =
      SqliteCanonicalSchemaManifest.loadNonCanonicalObjectsQuery();

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

  static final String FIND_LATE_OPENING_BALANCE_POSTING =
      """
      select opening.posting_id
      from posting_fact as opening
      where
          opening.posting_kind = 'OPENING_BALANCE'
          and exists (
              select 1
              from posting_fact as ordinary
              where
                  ordinary.posting_kind <> 'OPENING_BALANCE'
                  and ordinary.posting_order < opening.posting_order
          )
      limit 1
      """;

  static final String FIND_OPENING_BALANCE_NOMINAL_ACCOUNT =
      """
      select posting_fact.posting_id
      from journal_line
      inner join posting_fact on posting_fact.posting_id = journal_line.posting_id
      inner join account on account.account_code = journal_line.account_code
      where
          posting_fact.posting_kind = 'OPENING_BALANCE'
          and account.account_type not in ('ASSET', 'LIABILITY', 'EQUITY')
      limit 1
      """;

  static final String FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD =
      """
      select posting_fact.posting_id
      from posting_fact
      inner join interim_result_sweep
        on posting_fact.effective_date <= interim_result_sweep.effective_date_to
      inner join interim_result_sweep_posting
        on interim_result_sweep_posting.interim_result_sweep_order = interim_result_sweep.interim_result_sweep_order
      inner join posting_fact as close_posting
        on close_posting.posting_id = interim_result_sweep_posting.posting_id
      where
          posting_fact.posting_kind not in ('INTERIM_RESULT_SWEEP', 'FISCAL_YEAR_CLOSE')
          and posting_fact.posting_order > close_posting.posting_order
      limit 1
      """;

  static final String FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING =
      """
      select posting_fact.posting_id
      from posting_fact
      left join interim_result_sweep_posting
        on interim_result_sweep_posting.posting_id = posting_fact.posting_id
      left join fiscal_year_close_posting
        on fiscal_year_close_posting.posting_id = posting_fact.posting_id
      where
          (
              posting_fact.posting_kind = 'INTERIM_RESULT_SWEEP'
              and interim_result_sweep_posting.posting_id is null
          )
          or (
              posting_fact.posting_kind = 'FISCAL_YEAR_CLOSE'
              and fiscal_year_close_posting.posting_id is null
          )
      limit 1
      """;

  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK =
      """
      select interim_result_sweep_posting.posting_id
      from interim_result_sweep_posting
      inner join interim_result_sweep
        on interim_result_sweep.interim_result_sweep_order = interim_result_sweep_posting.interim_result_sweep_order
      inner join posting_fact
        on posting_fact.posting_id = interim_result_sweep_posting.posting_id
      where
          posting_fact.posting_kind <> 'INTERIM_RESULT_SWEEP'
          or posting_fact.source_channel <> 'CLI'
          or posting_fact.effective_date <> interim_result_sweep.effective_date_to
      limit 1
      """;

  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT =
      """
      select interim_result_sweep.interim_result_sweep_order
      from interim_result_sweep
      inner join account on account.account_code = interim_result_sweep.result_holding_account_code
      where
          account.account_type <> 'EQUITY'
          or account.active = 0
          or account.financial_position_line_classification <> 'RESULT_HOLDING'
      limit 1
      """;

  static final String FIND_INVALID_FISCAL_YEAR_CLOSE_LINK =
      """
      select fiscal_year_close_posting.posting_id
      from fiscal_year_close_posting
      inner join fiscal_year_close
        on fiscal_year_close.fiscal_year_close_order = fiscal_year_close_posting.fiscal_year_close_order
      inner join posting_fact
        on posting_fact.posting_id = fiscal_year_close_posting.posting_id
      where
          posting_fact.posting_kind <> 'FISCAL_YEAR_CLOSE'
          or posting_fact.source_channel <> 'CLI'
          or posting_fact.effective_date <> fiscal_year_close.effective_date_to
      limit 1
      """;

  static final String FIND_INVALID_FISCAL_YEAR_CLOSE_TARGET_ACCOUNT =
      """
      select fiscal_year_close.fiscal_year_close_order
      from fiscal_year_close
      inner join account as capital
        on capital.account_code = fiscal_year_close.capital_account_code
      inner join account as result_holding
        on result_holding.account_code = fiscal_year_close.result_holding_account_code
      inner join account as retained_accumulated
        on retained_accumulated.account_code = fiscal_year_close.retained_accumulated_account_code
      where
          capital.account_type <> 'EQUITY'
          or capital.active = 0
          or capital.financial_position_line_classification <> 'EQUITY_CONTRIBUTION'
          or result_holding.account_type <> 'EQUITY'
          or result_holding.active = 0
          or result_holding.financial_position_line_classification <> 'RESULT_HOLDING'
          or retained_accumulated.account_type <> 'EQUITY'
          or retained_accumulated.active = 0
          or retained_accumulated.financial_position_line_classification <> 'RETAINED_ACCUMULATED'
      limit 1
      """;

  static final String LOAD_PERSISTED_MONEY_AUDIT_ROWS =
      """
      select currency_code, amount_minor
      from journal_line
      inner join posting_fact on posting_fact.posting_id = journal_line.posting_id
      order by posting_fact.posting_order, journal_line.line_order
      """;

  static final String FIND_JOURNAL_LINE_OUTSIDE_FUNCTIONAL_CURRENCY =
      """
      select journal_line.posting_id
      from journal_line
      join book_identity
        on book_identity.singleton_id = 1
      where journal_line.currency_code <> book_identity.functional_currency_code
      limit 1
      """;

  private SqlitePostingIntegritySql() {}
}
