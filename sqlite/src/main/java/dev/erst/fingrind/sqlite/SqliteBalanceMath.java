package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import java.util.Objects;

/** Exact balance arithmetic shared by SQLite-backed balance and reporting reads. */
final class SqliteBalanceMath {
  private SqliteBalanceMath() {}

  static CurrencyBalance currencyBalance(
      CurrencyUnit currencyCode, long debitTotal, long creditTotal) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Money debitMoney = Money.ofMinorUnits(currencyCode, debitTotal);
    Money creditMoney = Money.ofMinorUnits(currencyCode, creditTotal);
    return CurrencyBalance.ofTotals(debitMoney, creditMoney);
  }

  static BalanceSide balanceSide(long signedMinorUnits) {
    if (signedMinorUnits == 0L) {
      return BalanceSide.ZERO;
    }
    return signedMinorUnits > 0L ? BalanceSide.DEBIT : BalanceSide.CREDIT;
  }

  static long absoluteMinorUnits(long signedMinorUnits) {
    try {
      return Math.absExact(signedMinorUnits);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "SQLite running balance exceeded the supported exact money range.", exception);
    }
  }
}
