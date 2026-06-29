package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;

/** Maps SQLite foreign-exchange attachment rows into public FinGrind FX contract records. */
final class SqliteForeignExchangeMapper {
  private SqliteForeignExchangeMapper() {}

  static ForeignExchangeDetails details(SqliteNativeStatement statement) {
    CurrencyUnit transactionCurrency =
        CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 0));
    CurrencyUnit functionalCurrency =
        CurrencyUnit.of(SqlitePostingMapper.requiredText(statement, 2));
    return new ForeignExchangeDetails(
        MonetaryAmount.of(Money.ofMinorUnits(transactionCurrency, statement.columnLong(1))),
        MonetaryAmount.of(Money.ofMinorUnits(functionalCurrency, statement.columnLong(3))),
        new QuotedExchangeRate(
            MonetaryAmount.of(Money.ofMinorUnits(transactionCurrency, statement.columnLong(4))),
            MonetaryAmount.of(Money.ofMinorUnits(functionalCurrency, statement.columnLong(5))),
            CanonicalTemporalText.parseLocalDate(
                SqlitePostingMapper.requiredText(statement, 6), "foreignExchange.quotedOn"),
            SqlitePostingMapper.requiredText(statement, 7)),
        ForeignExchangeTreatmentKind.fromWireValue(SqlitePostingMapper.requiredText(statement, 8)));
  }
}
