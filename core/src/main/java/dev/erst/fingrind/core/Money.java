package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact non-negative money value represented in one currency unit's minor units. */
public final class Money implements Comparable<Money> {
  private final CurrencyUnit currencyUnit;
  private final long minorUnits;

  private Money(CurrencyUnit currencyUnit, long minorUnits) {
    this.currencyUnit = Objects.requireNonNull(currencyUnit, "currencyUnit");
    if (minorUnits < 0) {
      throw new IllegalArgumentException("Money minor units must not be negative.");
    }
    this.minorUnits = minorUnits;
  }

  /** Creates one exact money value directly from minor units. */
  public static Money ofMinorUnits(CurrencyUnit currencyUnit, long minorUnits) {
    return new Money(currencyUnit, minorUnits);
  }

  /** Returns the maximum supported ASCII-digit count for exact non-negative minor units. */
  public static int maxMinorUnitsDigitCount() {
    return MoneyTextSupport.maxMinorUnitsDigitCount();
  }

  /** Returns one zero money value for the selected currency unit. */
  public static Money zero(CurrencyUnit currencyUnit) {
    return new Money(currencyUnit, 0L);
  }

  /** Parses one non-negative plain-decimal amount for one selected currency unit. */
  public static Money parse(CurrencyUnit currencyUnit, String amountText) {
    return MoneyTextSupport.parse(currencyUnit, amountText);
  }

  /** Convenience parser that resolves the currency unit from its code first. */
  public static Money parse(String currencyCode, String amountText) {
    return parse(CurrencyUnit.of(currencyCode), amountText);
  }

  /** Returns the authoritative currency unit. */
  public CurrencyUnit currencyUnit() {
    return currencyUnit;
  }

  /** Returns the exact stored minor units. */
  public long minorUnits() {
    return minorUnits;
  }

  /** Returns the fixed decimal scale required by this money's currency unit. */
  public int scale() {
    return currencyUnit.minorUnitScale();
  }

  /** Returns whether this money value is zero. */
  public boolean isZero() {
    return minorUnits == 0L;
  }

  /** Returns whether this money value is strictly positive. */
  public boolean isPositive() {
    return minorUnits > 0L;
  }

  /** Returns one exact canonical decimal string at the currency unit's scale. */
  public String canonicalDecimal() {
    return MoneyTextSupport.canonicalDecimal(minorUnits, scale());
  }

  /** Adds two money values in the same currency unit. */
  public Money plus(Money other) {
    MoneyInvariantSupport.requireSameCurrency(currencyUnit, other);
    return new Money(currencyUnit, Math.addExact(minorUnits, other.minorUnits));
  }

  /** Subtracts one smaller or equal money value from this value. */
  public Money minus(Money other) {
    MoneyInvariantSupport.requireSameCurrency(currencyUnit, other);
    if (minorUnits < other.minorUnits) {
      throw new IllegalArgumentException("Money subtraction would produce a negative result.");
    }
    return new Money(currencyUnit, Math.subtractExact(minorUnits, other.minorUnits));
  }

  @Override
  public int compareTo(Money other) {
    MoneyInvariantSupport.requireSameCurrency(currencyUnit, other);
    return Long.compare(minorUnits, other.minorUnits);
  }

  @Override
  public boolean equals(Object other) {
    return this == other
        || (other instanceof Money that
            && minorUnits == that.minorUnits
            && currencyUnit.equals(that.currencyUnit));
  }

  @Override
  public int hashCode() {
    return Objects.hash(currencyUnit, minorUnits);
  }

  @Override
  public String toString() {
    return "Money[currencyUnit="
        + currencyUnit
        + ", minorUnits="
        + minorUnits
        + ", canonicalDecimal="
        + canonicalDecimal()
        + "]";
  }
}
