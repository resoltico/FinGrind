package dev.erst.fingrind.core;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact monetary value in one declared currency. */
public record Money(CurrencyCode currencyCode, BigDecimal amount) {
  /** Validates and normalizes a monetary value while preserving exact decimal semantics. */
  public Money {
    Objects.requireNonNull(currencyCode, "currencyCode");
    Objects.requireNonNull(amount, "amount");
    try {
      amount = amount.stripTrailingZeros();
      if (amount.scale() < 0) {
        amount = amount.setScale(0);
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact range.", exception);
    }
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Money amount must not be negative.");
    }
  }
}
