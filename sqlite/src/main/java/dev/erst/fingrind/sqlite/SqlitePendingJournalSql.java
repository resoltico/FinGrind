package dev.erst.fingrind.sqlite;

/** Pending journal-line validation and persistence SQL. */
final class SqlitePendingJournalSql {
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

  private SqlitePendingJournalSql() {}
}
