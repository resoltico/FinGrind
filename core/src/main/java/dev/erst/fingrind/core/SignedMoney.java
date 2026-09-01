package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact signed monetary value for report-level net and adjustment facts. */
public final class SignedMoney implements Comparable<SignedMoney> {
  private final CurrencyUnit currencyUnit;
  private final long minorUnits;

  private SignedMoney(CurrencyUnit currencyUnit, long minorUnits) {
    this.currencyUnit = Objects.requireNonNull(currencyUnit, "currencyUnit");
    this.minorUnits = minorUnits;
  }

  /** Creates one signed exact amount from its currency and minor-unit value. */
  public static SignedMoney ofMinorUnits(CurrencyUnit currencyUnit, long minorUnits) {
    return new SignedMoney(currencyUnit, minorUnits);
  }

  /** Creates one signed amount from an ordinary non-negative money value. */
  public static SignedMoney of(Money money) {
    Objects.requireNonNull(money, "money");
    return new SignedMoney(money.currencyUnit(), money.minorUnits());
  }

  /** Returns signed zero in the selected currency. */
  public static SignedMoney zero(CurrencyUnit currencyUnit) {
    return new SignedMoney(currencyUnit, 0L);
  }

  /** Returns the authoritative currency. */
  public CurrencyUnit currencyUnit() {
    return currencyUnit;
  }

  /** Returns the exact signed minor-unit value. */
  public long minorUnits() {
    return minorUnits;
  }

  /** Returns whether this amount is exactly zero. */
  public boolean isZero() {
    return minorUnits == 0L;
  }

  /** Returns whether this amount is positive. */
  public boolean isPositive() {
    return minorUnits > 0L;
  }

  /** Returns whether this amount is negative. */
  public boolean isNegative() {
    return minorUnits < 0L;
  }

  /** Returns canonical decimal text with the currency's fixed scale. */
  public String canonicalDecimal() {
    return ExactDecimalTextSupport.canonicalDecimal(minorUnits, currencyUnit.minorUnitScale());
  }

  /** Adds two amounts in the same currency exactly. */
  public SignedMoney plus(SignedMoney other) {
    requireSameCurrency(other);
    return new SignedMoney(currencyUnit, Math.addExact(minorUnits, other.minorUnits));
  }

  /** Subtracts another amount in the same currency exactly. */
  public SignedMoney minus(SignedMoney other) {
    requireSameCurrency(other);
    return new SignedMoney(currencyUnit, Math.subtractExact(minorUnits, other.minorUnits));
  }

  /** Returns this amount with its sign reversed. */
  public SignedMoney negated() {
    return new SignedMoney(currencyUnit, Math.negateExact(minorUnits));
  }

  /** Returns the non-negative magnitude of this signed amount. */
  public Money magnitude() {
    return Money.ofMinorUnits(currencyUnit, Math.absExact(minorUnits));
  }

  @Override
  public int compareTo(SignedMoney other) {
    requireSameCurrency(other);
    return Long.compare(minorUnits, other.minorUnits);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof SignedMoney that
            && minorUnits == that.minorUnits
            && currencyUnit.equals(that.currencyUnit));
  }

  @Override
  public int hashCode() {
    return Objects.hash(currencyUnit, minorUnits);
  }

  @Override
  public String toString() {
    return "SignedMoney[currencyUnit="
        + currencyUnit
        + ", minorUnits="
        + minorUnits
        + ", canonicalDecimal="
        + canonicalDecimal()
        + "]";
  }

  private void requireSameCurrency(SignedMoney other) {
    Objects.requireNonNull(other, "other");
    if (!currencyUnit.equals(other.currencyUnit)) {
      throw new IllegalArgumentException("Signed money values must use the same currency.");
    }
  }
}
