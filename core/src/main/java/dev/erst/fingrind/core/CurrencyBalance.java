package dev.erst.fingrind.core;

import java.util.Objects;

/** One per-currency balance bucket for a declared account. */
public record CurrencyBalance(
    Money debitTotal, Money creditTotal, Money netAmount, BalanceSide balanceSide) {
  /** Validates one per-currency balance bucket. */
  public CurrencyBalance {
    Objects.requireNonNull(debitTotal, "debitTotal");
    Objects.requireNonNull(creditTotal, "creditTotal");
    Objects.requireNonNull(netAmount, "netAmount");
    Objects.requireNonNull(balanceSide, "balanceSide");
    if (!debitTotal.currencyCode().equals(creditTotal.currencyCode())
        || !debitTotal.currencyCode().equals(netAmount.currencyCode())) {
      throw new IllegalArgumentException("Currency balance totals must share one currencyCode.");
    }
  }
}
