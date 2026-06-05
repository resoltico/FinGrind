package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import java.util.Collections;

/** Builds behavioral SQL statements on top of the reviewed literal catalog. */
final class SqlitePostingSqlQueryBuilder {
  private SqlitePostingSqlQueryBuilder() {}

  static String listAccounts() {
    return SqlitePostingSqlLiterals.BASE_ACCOUNT_SELECT
        + " where (? is null or account_code > ?) order by account_code limit ?";
  }

  static String findAccountsByCodeCount(int accountCount) {
    if (accountCount < 1) {
      throw new IllegalArgumentException("Account lookup count must be at least one.");
    }
    return SqlitePostingSqlLiterals.BASE_ACCOUNT_SELECT
        + " where account_code in ("
        + String.join(", ", Collections.nCopies(accountCount, "?"))
        + ")";
  }

  static String listPostings(PostingHistoryQuery query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_POSTING_SELECT.length() + 256)
            .append(SqlitePostingSqlLiterals.BASE_POSTING_SELECT)
            .append(" where 1 = 1");
    if (query.accountCode().isPresent()) {
      sql.append(
          """
           and exists (
               select 1
               from journal_line
               where journal_line.posting_id = posting_fact.posting_id
                 and journal_line.account_code = ?
           )
          """);
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and effective_date <= ?");
    }
    if (query.cursor().isPresent()) {
      sql.append(
          """
           and (
               effective_date < ?
               or (effective_date = ? and recorded_at < ?)
               or (effective_date = ? and recorded_at = ? and posting_id < ?)
           )
          """);
    }
    sql.append(" order by effective_date desc, recorded_at desc, posting_id desc limit ?");
    return sql.toString();
  }

  static String loadAccountLinesForBalance(AccountBalanceCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.LOAD_ACCOUNT_LINES_FOR_BALANCE.length() + 96)
            .append(SqlitePostingSqlLiterals.LOAD_ACCOUNT_LINES_FOR_BALANCE);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and posting_fact.effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by posting_fact.effective_date, posting_fact.recorded_at, journal_line.line_order");
    return sql.toString();
  }

  static String loadTrialBalanceLines(TrialBalanceCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 96)
            .append(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT)
            .append(" where 1 = 1");
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'");
    }
    if (query.effectiveDateAsOf().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        " order by account.account_code, journal_line.currency_code, posting_fact.effective_date, posting_fact.recorded_at, posting_fact.posting_id");
    return sql.toString();
  }

  static String loadAccountTotals(TrialBalanceCriteria query) {
    return loadAccountTotals(
        query
            .effectiveDateAsOf()
            .map(EffectiveDateRange::to)
            .orElseGet(EffectiveDateRange::unbounded),
        query.postingCoverage());
  }

  static String loadAccountTotals(
      EffectiveDateRange effectiveDateRange, PostingCoverage postingCoverage) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 256)
            .append(
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
                    journal_line.currency_code,
                    sum(case when journal_line.entry_side = 'DEBIT' then journal_line.amount_minor else 0 end) as debit_minor,
                    sum(case when journal_line.entry_side = 'CREDIT' then journal_line.amount_minor else 0 end) as credit_minor
                from journal_line
                join posting_fact on posting_fact.posting_id = journal_line.posting_id
                join account on account.account_code = journal_line.account_code
                where 1 = 1
                """);
    if (postingCoverage.isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> 'PERIOD_RESULT_TRANSFER'");
    }
    if (effectiveDateRange.effectiveDateFrom().isPresent()) {
      sql.append(" and posting_fact.effective_date >= ?");
    }
    if (effectiveDateRange.effectiveDateTo().isPresent()) {
      sql.append(" and posting_fact.effective_date <= ?");
    }
    sql.append(
        """
         group by
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
             journal_line.currency_code
         order by account.account_code, journal_line.currency_code
        """);
    return sql.toString();
  }

  static String loadPeriodSummaryLines(PeriodSummaryCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT.length() + 128)
            .append(SqlitePostingSqlLiterals.BASE_REPORT_LINE_SELECT)
            .append(
                """
                 where posting_fact.effective_date >= ?
                   and posting_fact.effective_date <= ?
                """);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    sql.append(
        """
         order by posting_fact.effective_date, posting_fact.recorded_at, posting_fact.posting_id, journal_line.line_order
        """);
    return sql.toString();
  }

  static String listPostingsForAccountLedger(AccountLedgerCriteria query) {
    StringBuilder sql =
        new StringBuilder(SqlitePostingSqlLiterals.BASE_POSTING_SELECT.length() + 192)
            .append(SqlitePostingSqlLiterals.BASE_POSTING_SELECT)
            .append(
                """
                 where exists (
                     select 1
                     from journal_line
                     where journal_line.posting_id = posting_fact.posting_id
                       and journal_line.account_code = ?
                 )
                """);
    if (query.postingCoverage().isNonClosingOnly()) {
      sql.append(" and posting_fact.posting_kind <> ?");
    }
    if (query.effectiveDateRange().effectiveDateFrom().isPresent()) {
      sql.append(" and effective_date >= ?");
    }
    if (query.effectiveDateRange().effectiveDateTo().isPresent()) {
      sql.append(" and effective_date <= ?");
    }
    sql.append(" order by effective_date, recorded_at, posting_id");
    return sql.toString();
  }
}
