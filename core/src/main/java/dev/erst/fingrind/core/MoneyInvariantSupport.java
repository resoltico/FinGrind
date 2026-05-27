package dev.erst.fingrind.core;

import java.util.Objects;

/** Shared invariant checks for exact money operations. */
final class MoneyInvariantSupport {
  private MoneyInvariantSupport() {}

  static void requireSameCurrency(CurrencyUnit currencyUnit, Money other) {
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    Objects.requireNonNull(other, "other");
    if (!currencyUnit.equals(other.currencyUnit())) {
      throw new IllegalArgumentException("Money values must share one currency unit.");
    }
  }
}
