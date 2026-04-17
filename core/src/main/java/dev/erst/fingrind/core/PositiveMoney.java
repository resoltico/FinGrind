package dev.erst.fingrind.core;

import java.math.BigDecimal;
import java.util.Objects;

/** Exact strictly positive monetary value in one declared currency. */
public record PositiveMoney(Money value) {
  /** Validates one strictly positive monetary value. */
  public PositiveMoney {
    Objects.requireNonNull(value, "value");
    if (value.amount().signum() == 0) {
      throw new IllegalArgumentException("Journal line amount must be greater than zero.");
    }
  }

  /** Creates one strictly positive monetary value directly from currency and amount inputs. */
  public PositiveMoney(CurrencyCode currencyCode, BigDecimal amount) {
    this(new Money(currencyCode, amount));
  }

  /** Returns the currency carried by the positive money value. */
  public CurrencyCode currencyCode() {
    return value.currencyCode();
  }

  /** Returns the exact positive decimal amount. */
  public BigDecimal amount() {
    return value.amount();
  }
}
