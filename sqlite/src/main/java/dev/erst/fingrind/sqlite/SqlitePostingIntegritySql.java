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

  static final String FIND_JOURNAL_LINE_ON_INACTIVE_ACCOUNT =
      """
      select journal_line.posting_id
      from journal_line
      inner join account on account.account_code = journal_line.account_code
      where account.active = 0
      limit 1
      """;

  static final String FIND_POSTING_RECORDED_AFTER_CLOSED_PERIOD =
      """
      select posting_fact.posting_id
      from posting_fact
      inner join period_result_transfer
        on posting_fact.effective_date <= period_result_transfer.effective_date_to
      inner join period_result_transfer_posting
        on period_result_transfer_posting.period_result_transfer_order = period_result_transfer.period_result_transfer_order
      inner join posting_fact as close_posting
        on close_posting.posting_id = period_result_transfer_posting.posting_id
      where
          posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'
          and posting_fact.posting_order > close_posting.posting_order
      limit 1
      """;

  static final String FIND_UNLINKED_PERIOD_RESULT_TRANSFER_POSTING =
      """
      select posting_fact.posting_id
      from posting_fact
      left join period_result_transfer_posting on period_result_transfer_posting.posting_id = posting_fact.posting_id
      where
          posting_fact.posting_kind = 'PERIOD_RESULT_TRANSFER'
          and period_result_transfer_posting.posting_id is null
      limit 1
      """;

  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_LINK =
      """
      select period_result_transfer_posting.posting_id
      from period_result_transfer_posting
      inner join period_result_transfer
        on period_result_transfer.period_result_transfer_order = period_result_transfer_posting.period_result_transfer_order
      inner join posting_fact
        on posting_fact.posting_id = period_result_transfer_posting.posting_id
      where
          posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'
          or posting_fact.actor_type <> 'SYSTEM'
          or posting_fact.source_channel <> 'SYSTEM'
          or posting_fact.effective_date <> period_result_transfer.effective_date_to
      limit 1
      """;

  static final String FIND_INVALID_PERIOD_RESULT_TRANSFER_TARGET_ACCOUNT =
      """
      select period_result_transfer.period_result_transfer_order
      from period_result_transfer
      inner join account on account.account_code = period_result_transfer.closing_equity_account_code
      where
          account.account_type <> 'EQUITY'
          or account.active = 0
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
