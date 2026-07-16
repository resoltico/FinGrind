package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.FinancingArrangementId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.FinancingArrangementRecord;
import java.time.LocalDate;
import java.util.Optional;

/** Durable read mapping for active financing arrangements and their unreversed applications. */
final class SqliteFinancingStatementQueries {
  private static final String ACTIVE_ARRANGEMENTS =
      """
      select arrangement.financing_arrangement_id, arrangement.originated_on,
             arrangement.principal_liability_account_code, arrangement.interest_payable_account_code,
             arrangement.currency_code, arrangement.original_principal_minor,
             coalesce(sum(case when application.application_kind = 'PRINCIPAL_REPAYMENT' then application.amount_minor else 0 end), 0),
             coalesce(sum(case when application.application_kind = 'INTEREST_ACCRUAL' then application.amount_minor else 0 end), 0),
             coalesce(sum(case when application.application_kind = 'INTEREST_PAYMENT' then application.amount_minor else 0 end), 0),
             (
                 select max(history.effective_date)
                 from financing_application history
                 where history.financing_arrangement_id = arrangement.financing_arrangement_id
             )
      from financing_arrangement arrangement
      left join financing_application application
          on application.financing_arrangement_id = arrangement.financing_arrangement_id
          and not exists (
              select 1
              from financing_application_reversal reversal
              where reversal.application_posting_id = application.application_posting_id
          )
      where not exists (
          select 1
          from financing_arrangement_reversal reversal
          where reversal.financing_arrangement_id = arrangement.financing_arrangement_id
      )
      group by arrangement.financing_arrangement_id
      order by arrangement.originated_on, arrangement.financing_arrangement_id
      """;

  private SqliteFinancingStatementQueries() {}

  static Optional<FinancingArrangementRecord> find(
      SqliteNativeDatabase database, FinancingArrangementId financingArrangementId) {
    return SqliteLifecycleStatementQuerySupport.findOne(
        database,
        ACTIVE_ARRANGEMENTS.replace(
            "group by arrangement.financing_arrangement_id",
            "and arrangement.financing_arrangement_id = ?\n      group by arrangement.financing_arrangement_id"),
        financingArrangementId.value(),
        statement -> statement.bindText(1, financingArrangementId.value()),
        SqliteFinancingStatementQueries::map,
        "financing arrangement");
  }

  static java.util.List<FinancingArrangementRecord> load(SqliteNativeDatabase database) {
    return SqliteLifecycleStatementQuerySupport.loadAll(
        database, ACTIVE_ARRANGEMENTS, SqliteFinancingStatementQueries::map);
  }

  static boolean exists(
      SqliteNativeDatabase database, FinancingArrangementId financingArrangementId) {
    return SqliteLifecycleStatementQuerySupport.exists(
        database,
        "select 1 from financing_arrangement where financing_arrangement_id = ?",
        financingArrangementId.value(),
        statement -> statement.bindText(1, financingArrangementId.value()));
  }

  private static FinancingArrangementRecord map(SqliteNativeStatement statement) {
    CurrencyUnit currency = CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 4));
    return new FinancingArrangementRecord(
        new FinancingArrangementId(SqlitePostingMapper.requiredText(statement, 0)),
        date(statement, 1, "financingArrangement.originatedOn"),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 3)),
        Money.ofMinorUnits(currency, statement.columnLong(5)),
        Money.ofMinorUnits(currency, statement.columnLong(6)),
        Money.ofMinorUnits(currency, statement.columnLong(7)),
        Money.ofMinorUnits(currency, statement.columnLong(8)),
        Optional.ofNullable(statement.columnText(9))
            .map(
                value ->
                    CanonicalTemporalText.parseLocalDate(
                        value, "financingArrangement.lifecycleHorizon")));
  }

  private static LocalDate date(SqliteNativeStatement statement, int index, String fieldName) {
    return CanonicalTemporalText.parseLocalDate(
        SqlitePostingMapper.requiredText(statement, index), fieldName);
  }
}
