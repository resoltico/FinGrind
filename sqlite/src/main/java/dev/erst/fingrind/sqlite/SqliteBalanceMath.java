package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.Money;
import java.math.BigDecimal;
import java.util.Objects;

/** Exact balance arithmetic shared by SQLite-backed balance and reporting reads. */
final class SqliteBalanceMath {
  private SqliteBalanceMath() {}

  static CurrencyBalance currencyBalance(
      CurrencyCode currencyCode, BigDecimal debitTotal, BigDecimal creditTotal) {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(debitTotal, "debitTotal");
    Objects.requireNonNull(creditTotal, "creditTotal");
    BigDecimal net = debitTotal.subtract(creditTotal);
    BigDecimal absoluteNet = net.abs();
    BalanceSide balanceSide = net.signum() > 0 ? BalanceSide.DEBIT : BalanceSide.CREDIT;
    if (absoluteNet.signum() == 0) {
      balanceSide = BalanceSide.ZERO;
    }
    return new CurrencyBalance(
        new Money(currencyCode, debitTotal),
        new Money(currencyCode, creditTotal),
        new Money(currencyCode, absoluteNet),
        balanceSide);
  }
}
