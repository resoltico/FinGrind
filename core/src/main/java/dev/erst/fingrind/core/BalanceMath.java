package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact balance arithmetic shared across bookkeeping read and reporting surfaces. */
public final class BalanceMath {
  private BalanceMath() {}

  /** Builds one currency balance from exact debit and credit totals. */
  public static CurrencyBalance currencyBalance(
      CurrencyUnit currencyUnit, long debitTotal, long creditTotal) {
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    Money debitMoney = Money.ofMinorUnits(currencyUnit, debitTotal);
    Money creditMoney = Money.ofMinorUnits(currencyUnit, creditTotal);
    return CurrencyBalance.ofTotals(debitMoney, creditMoney);
  }

  /** Derives the signed-balance side for one exact signed minor-unit total. */
  public static BalanceSide balanceSide(long signedMinorUnits) {
    if (signedMinorUnits == 0L) {
      return BalanceSide.ZERO;
    }
    return signedMinorUnits > 0L ? BalanceSide.DEBIT : BalanceSide.CREDIT;
  }

  /** Returns the absolute exact minor units for one signed running balance. */
  public static long absoluteMinorUnits(long signedMinorUnits) {
    try {
      return Math.absExact(signedMinorUnits);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException(
          "Running balance exceeded the supported exact money range.", exception);
    }
  }
}
