package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact per-currency grouped balance bucket derived from debit and credit totals. */
public record CurrencyBalance(Money debitTotal, Money creditTotal) {
  /** Validates the grouped debit and credit totals. */
  public CurrencyBalance {
    Objects.requireNonNull(debitTotal, "debitTotal");
    Objects.requireNonNull(creditTotal, "creditTotal");
    if (!debitTotal.currencyUnit().equals(creditTotal.currencyUnit())) {
      throw new IllegalArgumentException("Currency balance totals must share one currency unit.");
    }
  }

  /** Derives one consistent balance bucket from debit and credit totals in one currency unit. */
  public static CurrencyBalance ofTotals(Money debitTotal, Money creditTotal) {
    return new CurrencyBalance(debitTotal, creditTotal);
  }

  /** Returns the absolute net amount. */
  public Money netAmount() {
    return switch (balanceSide()) {
      case DEBIT -> debitTotal.minus(creditTotal);
      case CREDIT -> creditTotal.minus(debitTotal);
      case ZERO -> Money.zero(debitTotal.currencyUnit());
    };
  }

  /** Returns the sign carrier for the net amount. */
  public BalanceSide balanceSide() {
    int comparison = debitTotal.compareTo(creditTotal);
    if (comparison > 0) {
      return BalanceSide.DEBIT;
    }
    if (comparison < 0) {
      return BalanceSide.CREDIT;
    }
    return BalanceSide.ZERO;
  }

  @Override
  public String toString() {
    return "CurrencyBalance[debitTotal="
        + debitTotal
        + ", creditTotal="
        + creditTotal
        + ", netAmount="
        + netAmount()
        + ", balanceSide="
        + balanceSide()
        + "]";
  }
}
