package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PositiveMoney;
import java.util.Objects;

/** Encodes and decodes the canonical persisted SQLite money representation. */
final class SqlitePersistedMoneyCodec {
  private SqlitePersistedMoneyCodec() {}

  static void bindPositiveMoney(
      SqliteNativeStatement statement,
      int currencyCodeParameterIndex,
      int amountMinorParameterIndex,
      PositiveMoney amount) {
    Objects.requireNonNull(statement, "statement");
    Objects.requireNonNull(amount, "amount");
    statement.bindText(currencyCodeParameterIndex, amount.currencyUnit().code());
    statement.bindLong(amountMinorParameterIndex, amount.minorUnits());
  }

  static Money readMoney(
      SqliteNativeStatement statement, int currencyCodeColumnIndex, int amountMinorColumnIndex) {
    Objects.requireNonNull(statement, "statement");
    CurrencyUnit currencyUnit = readCurrencyUnit(statement, currencyCodeColumnIndex);
    long amountMinor = statement.columnLong(amountMinorColumnIndex);
    if (amountMinor < 0L) {
      throw new IllegalStateException("Persisted SQLite money minor units must not be negative.");
    }
    return Money.ofMinorUnits(currencyUnit, amountMinor);
  }

  static PositiveMoney readPositiveMoney(
      SqliteNativeStatement statement, int currencyCodeColumnIndex, int amountMinorColumnIndex) {
    return PositiveMoney.of(readMoney(statement, currencyCodeColumnIndex, amountMinorColumnIndex));
  }

  static CurrencyUnit readCurrencyUnit(
      SqliteNativeStatement statement, int currencyCodeColumnIndex) {
    Objects.requireNonNull(statement, "statement");
    String currencyCode = SqlitePostingMapper.requiredText(statement, currencyCodeColumnIndex);
    return readCurrencyUnit(currencyCode);
  }

  static CurrencyUnit readCurrencyUnit(String currencyCode) {
    return CurrencyUnit.of(currencyCode);
  }
}
