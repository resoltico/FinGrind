package dev.erst.fingrind.contract;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import java.util.Objects;

/** One per-currency balance bucket for a declared account. */
public record CurrencyBalance(
    Money debitTotal, Money creditTotal, Money netAmount, NormalBalance balanceSide) {
  /** Validates one per-currency balance bucket. */
  public CurrencyBalance {
    Objects.requireNonNull(debitTotal, "debitTotal");
    Objects.requireNonNull(creditTotal, "creditTotal");
    Objects.requireNonNull(netAmount, "netAmount");
    Objects.requireNonNull(balanceSide, "balanceSide");
  }
}
