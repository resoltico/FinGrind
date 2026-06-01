package dev.erst.fingrind.sqlite;

/** Canonical SQL statements for the SQLite posting adapter. */
final class SqlitePostingSqlLiterals {
  static final String INITIALIZED_AT_META_KEY = "initialized_at";
  static final String SCHEMA_FINGERPRINT_META_KEY = "schema_fingerprint_sha256";

  static final int COL_POSTING_ID = 0;
  static final int COL_POSTING_KIND = 1;
  static final int COL_POSTING_ORIGIN_KIND = 2;
  static final int COL_EFFECTIVE_DATE = 3;
  static final int COL_RECORDED_AT = 4;
  static final int COL_ACTOR_ID = 5;
  static final int COL_ACTOR_TYPE = 6;
  static final int COL_COMMAND_ID = 7;
  static final int COL_IDEMPOTENCY_KEY = 8;
  static final int COL_CAUSATION_ID = 9;
  static final int COL_CORRELATION_ID = 10;
  static final int COL_REASON = 11;
  static final int COL_SOURCE_CHANNEL = 12;
  static final int COL_PRIOR_POSTING_ID = 13;

  static final int COL_LINE_ACCOUNT_CODE = 0;
  static final int COL_LINE_ENTRY_SIDE = 1;
  static final int COL_LINE_CURRENCY_CODE = 2;
  static final int COL_LINE_AMOUNT_MINOR = 3;

  static final int COL_SOURCE_DOCUMENT_ID = 0;
  static final int COL_SOURCE_DOCUMENT_TYPE = 1;
  static final int COL_SOURCE_DOCUMENT_DATE = 2;
  static final int COL_SOURCE_DOCUMENT_CAPTURED_AT = 3;
  static final int COL_SOURCE_DOCUMENT_STORAGE_LOCATOR = 4;
  static final int COL_SOURCE_DOCUMENT_CONTENT_SHA256 = 5;

  static final int COL_APPROVAL_ID = 0;
  static final int COL_APPROVAL_TYPE = 1;
  static final int COL_APPROVER_ID = 2;
  static final int COL_APPROVER_TYPE = 3;
  static final int COL_APPROVAL_DECISION = 4;
  static final int COL_APPROVED_AT = 5;

  static final int COL_ACCOUNT_CODE = 0;
  static final int COL_ACCOUNT_NAME = 1;
  static final int COL_ACCOUNT_TYPE = 2;
  static final int COL_ACCOUNT_ROLE = 3;
  static final int COL_ACCOUNT_NODE_KIND = 4;
  static final int COL_ACCOUNT_PARENT_ACCOUNT_CODE = 5;
  static final int COL_ACCOUNT_FINANCIAL_POSITION_LINE_CLASSIFICATION = 6;
  static final int COL_ACCOUNT_PROFIT_AND_LOSS_LINE_CLASSIFICATION = 7;
  static final int COL_ACCOUNT_ACTIVE = 8;
  static final int COL_ACCOUNT_DECLARED_AT = 9;
  static final int COL_REPORT_POSTING_ID = 10;
  static final int COL_REPORT_ENTRY_SIDE = 11;
  static final int COL_REPORT_CURRENCY_CODE = 12;
  static final int COL_REPORT_AMOUNT_MINOR = 13;
  static final int COL_TOTAL_CURRENCY_CODE = 10;
  static final int COL_TOTAL_DEBIT_MINOR = 11;
  static final int COL_TOTAL_CREDIT_MINOR = 12;

  static final String BASE_POSTING_SELECT =
      """
      select
          posting_id,
          posting_kind,
          posting_origin_kind,
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

  static final String BASE_ACCOUNT_SELECT =
      """
      select
          account_code,
          account_name,
          account_type,
          account_role,
          account_node_kind,
          parent_account_code,
          financial_position_line_classification,
          profit_and_loss_line_classification,
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
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_INITIALIZED_AT =
      """
      select value
      from book_meta
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_META_VALUE =
      """
      select value
      from book_meta
      where meta_key = ?
      limit 1
      """;

  static final String FIND_BOOK_IDENTITY_CORE =
      """
      select
          entity_name,
          accounting_kernel_profile,
          functional_currency_code,
          fiscal_year_start_month,
          fiscal_year_start_day
      from book_identity
      where singleton_id = 1
      limit 1
      """;

  static final String FIND_ENTITY_PROFILE =
      """
      select
          business_activity_tags
      from entity_profile
      where singleton_id = 1
      limit 1
      """;

  static final String PRAGMA_INTEGRITY_CHECK = "pragma integrity_check";
  static final String PRAGMA_FOREIGN_KEY_CHECK = "pragma foreign_key_check";

  static final int EXPECTED_CANONICAL_SCHEMA_OBJECT_COUNT =
      SqliteCanonicalSchemaManifest.objectCount();

  static final String LOAD_CANONICAL_SCHEMA_OBJECTS =
      SqliteCanonicalSchemaManifest.loadObjectsQuery();

  static final String LOAD_NON_CANONICAL_SCHEMA_OBJECTS =
      SqliteCanonicalSchemaManifest.loadNonCanonicalObjectsQuery();

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
      select account_code, entry_side, currency_code, amount_minor
      from journal_line
      where posting_id = ?
      order by line_order
      """;

  static final String LOAD_SOURCE_DOCUMENTS =
      """
      select
          source_document_id,
          source_document_type,
          document_date,
          captured_at,
          storage_locator,
          content_sha256
      from posting_source_document
      where posting_id = ?
      order by source_document_order
      """;

  static final String LOAD_APPROVALS =
      """
      select
          approval_id,
          approval_type,
          approver_id,
          approver_type,
          decision,
          approved_at
      from posting_approval
      where posting_id = ?
      order by approval_order
      """;

  static final String LOAD_ACCOUNT_LINES_FOR_BALANCE =
      """
      select
          journal_line.entry_side,
          journal_line.currency_code,
          journal_line.amount_minor
      from journal_line
      join posting_fact on posting_fact.posting_id = journal_line.posting_id
      where journal_line.account_code = ?
      """;

  static final String BASE_REPORT_LINE_SELECT =
      """
      select
          account.account_code,
          account.account_name,
          account.account_type,
          account.account_role,
          account.account_node_kind,
          account.parent_account_code,
          account.financial_position_line_classification,
          account.profit_and_loss_line_classification,
          account.active,
          account.declared_at,
          posting_fact.posting_id,
          journal_line.entry_side,
          journal_line.currency_code,
          journal_line.amount_minor
      from journal_line
      join posting_fact on posting_fact.posting_id = journal_line.posting_id
      join account on account.account_code = journal_line.account_code
      """;

  static final String INSERT_POSTING_FACT =
      """
      insert into posting_fact (
          posting_id,
          posting_kind,
          posting_origin_kind,
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
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_JOURNAL_LINE =
      """
      insert into journal_line (
          posting_id,
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      ) values (?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_POSTING_SOURCE_DOCUMENT =
      """
      insert into posting_source_document (
          posting_id,
          source_document_order,
          source_document_id,
          source_document_type,
          document_date,
          captured_at,
          storage_locator,
          content_sha256
      ) values (?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_POSTING_APPROVAL =
      """
      insert into posting_approval (
          posting_id,
          approval_order,
          approval_id,
          approval_type,
          approver_id,
          approver_type,
          decision,
          approved_at
      ) values (?, ?, ?, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_AUDIT_EVENT =
      """
      insert into audit_event (
          recorded_at,
          event_kind,
          account_code,
          posting_id,
          period_result_transfer_order
      ) values (?, ?, ?, ?, ?)
      """;

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

  static final String LOAD_ALL_ACCOUNTS = BASE_ACCOUNT_SELECT + " order by account_code";

  static final String LOAD_POSTINGS_IN_RANGE =
      BASE_POSTING_SELECT
          + """
             where (? is null or effective_date >= ?)
               and (? is null or effective_date <= ?)
             order by effective_date, recorded_at, posting_id
             """;

  static final String CREATE_PENDING_JOURNAL_LINE =
      """
      create temporary table if not exists pending_journal_line (
          line_order integer not null check (line_order >= 0),
          account_code text not null,
          entry_side text not null check (entry_side in ('DEBIT', 'CREDIT')),
          currency_code text not null,
          amount_minor integer not null check (amount_minor > 0)
      ) strict
      """;

  static final String CLEAR_PENDING_JOURNAL_LINE = "delete from pending_journal_line";

  static final String INSERT_PENDING_JOURNAL_LINE =
      """
      insert into pending_journal_line (
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      ) values (?, ?, ?, ?, ?)
      """;

  static final String VALID_PENDING_JOURNAL_LINE =
      """
      select 1
      from (
          select
              count(*) as line_count,
              sum(case when entry_side = 'DEBIT' then 1 else 0 end) as debit_count,
              sum(case when entry_side = 'CREDIT' then 1 else 0 end) as credit_count,
              count(distinct currency_code) as currency_bucket_count,
              sum(case when entry_side = 'DEBIT' then amount_minor else -amount_minor end) as signed_minor_total
          from pending_journal_line
      )
      where line_count >= 2
        and debit_count >= 1
        and credit_count >= 1
        and currency_bucket_count = 1
        and signed_minor_total = 0
      limit 1
      """;

  static final String PERSIST_PENDING_JOURNAL_LINE =
      """
      insert into journal_line (
          posting_id,
          line_order,
          account_code,
          entry_side,
          currency_code,
          amount_minor
      )
      select ?, line_order, account_code, entry_side, currency_code, amount_minor
      from pending_journal_line
      order by line_order
      """;

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

  static final String INSERT_BOOK_META_VALUE =
      """
      insert into book_meta (meta_key, value)
      values (?, ?)
      """;

  static final String INSERT_BOOK_IDENTITY =
      """
      insert into book_identity (
          singleton_id,
          entity_name,
          accounting_kernel_profile,
          functional_currency_code,
          fiscal_year_start_month,
          fiscal_year_start_day
      ) values (1, ?, ?, ?, ?, ?)
      """;

  static final String INSERT_ENTITY_PROFILE =
      """
      insert into entity_profile (
          singleton_id,
          business_activity_tags
      ) values (1, ?)
      """;

  static final String UPSERT_ACCOUNT =
      """
      insert into account (
          account_code,
          account_name,
          account_type,
          account_role,
          account_node_kind,
          parent_account_code,
          financial_position_line_classification,
          profit_and_loss_line_classification,
          active,
          declared_at
      ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      on conflict (account_code) do update set
          account_name = excluded.account_name,
          active = excluded.active,
          declared_at = excluded.declared_at
      """;

  private SqlitePostingSqlLiterals() {}
}
