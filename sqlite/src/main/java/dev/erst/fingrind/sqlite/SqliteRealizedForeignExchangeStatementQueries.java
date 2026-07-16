package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.ForeignCurrencyObligationId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.executor.bookkeeping.ForeignCurrencyObligationRecord;
import java.time.LocalDate;
import java.util.Optional;

/** Durable read mapping for active foreign-currency obligations and settlements. */
final class SqliteRealizedForeignExchangeStatementQueries {
  private static final String ACTIVE_OBLIGATIONS =
      """
      select obligation.foreign_currency_obligation_id, obligation.originated_on,
             obligation.receivable_account_code, obligation.realized_gain_account_code,
             obligation.realized_loss_account_code, obligation.transaction_currency_code,
             obligation.transaction_amount_minor, obligation.functional_currency_code,
             obligation.functional_carrying_amount_minor, settlement.effective_date,
             settlement.functional_currency_code, settlement.functional_settlement_amount_minor,
             (
                 select max(history.effective_date)
                 from foreign_currency_obligation_settlement history
                 where history.foreign_currency_obligation_id = obligation.foreign_currency_obligation_id
             )
      from foreign_currency_obligation obligation
      left join foreign_currency_obligation_settlement settlement
          on settlement.foreign_currency_obligation_id = obligation.foreign_currency_obligation_id
          and not exists (
              select 1
              from foreign_currency_obligation_settlement_reversal reversal
              where reversal.settlement_posting_id = settlement.settlement_posting_id
          )
      where not exists (
          select 1
          from foreign_currency_obligation_reversal reversal
          where reversal.foreign_currency_obligation_id = obligation.foreign_currency_obligation_id
      )
      order by obligation.originated_on, obligation.foreign_currency_obligation_id
      """;

  private SqliteRealizedForeignExchangeStatementQueries() {}

  static Optional<ForeignCurrencyObligationRecord> find(
      SqliteNativeDatabase database, ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return SqliteLifecycleStatementQuerySupport.findOne(
        database,
        ACTIVE_OBLIGATIONS.replace(
            "order by obligation.originated_on, obligation.foreign_currency_obligation_id",
            "and obligation.foreign_currency_obligation_id = ?\n      order by obligation.originated_on, obligation.foreign_currency_obligation_id"),
        foreignCurrencyObligationId.value(),
        statement -> statement.bindText(1, foreignCurrencyObligationId.value()),
        SqliteRealizedForeignExchangeStatementQueries::map,
        "foreign-currency obligation");
  }

  static java.util.List<ForeignCurrencyObligationRecord> load(SqliteNativeDatabase database) {
    return SqliteLifecycleStatementQuerySupport.loadAll(
        database, ACTIVE_OBLIGATIONS, SqliteRealizedForeignExchangeStatementQueries::map);
  }

  static boolean exists(
      SqliteNativeDatabase database, ForeignCurrencyObligationId foreignCurrencyObligationId) {
    return SqliteLifecycleStatementQuerySupport.exists(
        database,
        "select 1 from foreign_currency_obligation where foreign_currency_obligation_id = ?",
        foreignCurrencyObligationId.value(),
        statement -> statement.bindText(1, foreignCurrencyObligationId.value()));
  }

  private static ForeignCurrencyObligationRecord map(SqliteNativeStatement statement) {
    CurrencyUnit transactionCurrency =
        CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 5));
    CurrencyUnit functionalCurrency =
        CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 7));
    return new ForeignCurrencyObligationRecord(
        new ForeignCurrencyObligationId(SqlitePostingMapper.requiredText(statement, 0)),
        date(statement, 1, "foreignCurrencyObligation.originatedOn"),
        Optional.ofNullable(statement.columnText(12))
            .map(
                value ->
                    CanonicalTemporalText.parseLocalDate(
                        value, "foreignCurrencyObligation.lifecycleHorizon"))
            .orElseGet(() -> date(statement, 1, "foreignCurrencyObligation.originatedOn")),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 2)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 3)),
        new AccountCode(SqlitePostingMapper.requiredText(statement, 4)),
        Money.ofMinorUnits(transactionCurrency, statement.columnLong(6)),
        Money.ofMinorUnits(functionalCurrency, statement.columnLong(8)),
        Optional.ofNullable(statement.columnText(9))
            .map(
                value ->
                    CanonicalTemporalText.parseLocalDate(
                        value, "foreignCurrencyObligation.settledOn")),
        optionalSettlementAmount(statement),
        optionalRealizedGainOrLoss(statement),
        Optional.ofNullable(statement.columnText(9))
            .map(ignored -> statement.columnLong(11) >= statement.columnLong(8)));
  }

  private static Optional<Money> optionalSettlementAmount(SqliteNativeStatement statement) {
    return Optional.ofNullable(statement.columnText(10))
        .map(currency -> Money.ofMinorUnits(CurrencyUnit.of(currency), statement.columnLong(11)));
  }

  private static Optional<Money> optionalRealizedGainOrLoss(SqliteNativeStatement statement) {
    return optionalSettlementAmount(statement)
        .map(
            settlement ->
                Money.ofMinorUnits(
                    settlement.currencyUnit(),
                    Math.abs(settlement.minorUnits() - statement.columnLong(8))));
  }

  private static LocalDate date(SqliteNativeStatement statement, int index, String fieldName) {
    return CanonicalTemporalText.parseLocalDate(
        SqlitePostingMapper.requiredText(statement, index), fieldName);
  }
}
