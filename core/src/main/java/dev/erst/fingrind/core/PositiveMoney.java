package dev.erst.fingrind.core;

import java.util.Objects;

/** Strictly positive posted money value used for journal-line amounts. */
public final class PositiveMoney {
  private final Money value;

  private PositiveMoney(Money value) {
    this.value = Objects.requireNonNull(value, "value");
    if (!value.isPositive()) {
      throw new IllegalArgumentException("Journal line amount must be greater than zero.");
    }
  }

  /** Lifts one exact money value into a journal-line-positive money value. */
  public static PositiveMoney of(Money value) {
    return new PositiveMoney(value);
  }

  /** Parses one positive money amount for one selected currency unit. */
  public static PositiveMoney parse(CurrencyUnit currencyUnit, String amountText) {
    return new PositiveMoney(Money.parse(currencyUnit, amountText));
  }

  /** Returns the exact underlying non-negative money value. */
  public Money money() {
    return value;
  }

  /** Returns the journal-line currency unit. */
  public CurrencyUnit currencyUnit() {
    return value.currencyUnit();
  }

  /** Returns the exact positive minor units. */
  public long minorUnits() {
    return value.minorUnits();
  }

  /** Returns one exact canonical decimal string at the currency unit's scale. */
  public String canonicalDecimal() {
    return value.canonicalDecimal();
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof PositiveMoney that && value.equals(that.value));
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "PositiveMoney[value=" + value + "]";
  }
}
