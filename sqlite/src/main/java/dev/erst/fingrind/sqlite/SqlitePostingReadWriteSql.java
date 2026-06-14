package dev.erst.fingrind.sqlite;

/** Canonical account, posting, source-document, approval, and mutation SQL. */
final class SqlitePostingReadWriteSql {
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

  static final String LOAD_ALL_ACCOUNTS = BASE_ACCOUNT_SELECT + " order by account_code";

  static final String LOAD_POSTINGS_IN_RANGE =
      BASE_POSTING_SELECT
          + """
             where (? is null or effective_date >= ?)
               and (? is null or effective_date <= ?)
             order by effective_date, recorded_at, posting_id
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

  private SqlitePostingReadWriteSql() {}
}
