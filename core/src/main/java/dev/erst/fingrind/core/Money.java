package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact non-negative money value represented in one currency unit's minor units. */
public final class Money implements Comparable<Money> {
  private static final int MAX_MINOR_UNITS_DIGIT_COUNT = Long.toString(Long.MAX_VALUE).length();

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
    return MAX_MINOR_UNITS_DIGIT_COUNT;
  }

  /** Returns one zero money value for the selected currency unit. */
  public static Money zero(CurrencyUnit currencyUnit) {
    return new Money(currencyUnit, 0L);
  }

  /** Parses one non-negative plain-decimal amount for one selected currency unit. */
  public static Money parse(CurrencyUnit currencyUnit, String amountText) {
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    Objects.requireNonNull(amountText, "amountText");
    if (!amountText.equals(amountText.strip())) {
      throw new IllegalArgumentException(
          "Money amount must not contain leading or trailing space.");
    }
    if (amountText.isEmpty()) {
      throw new IllegalArgumentException("Money amount must not be blank.");
    }
    if (amountText.startsWith("+") || amountText.startsWith("-")) {
      throw new IllegalArgumentException("Money amount must be non-negative and unsigned.");
    }
    if (amountText.indexOf('e') >= 0 || amountText.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          "Money amount must be a plain decimal string without exponent notation.");
    }
    int decimalPointIndex = amountText.indexOf('.');
    if (decimalPointIndex != amountText.lastIndexOf('.')) {
      throw new IllegalArgumentException("Money amount must contain at most one decimal point.");
    }
    String wholeUnitsText =
        decimalPointIndex >= 0 ? amountText.substring(0, decimalPointIndex) : amountText;
    String fractionalText =
        decimalPointIndex >= 0 ? amountText.substring(decimalPointIndex + 1) : "";
    requireDigits(wholeUnitsText, "Money amount must contain decimal digits.");
    if (wholeUnitsText.length() > 1 && wholeUnitsText.startsWith("0")) {
      throw new IllegalArgumentException("Money amount must not contain redundant leading zeroes.");
    }
    if (wholeUnitsText.length() > MAX_MINOR_UNITS_DIGIT_COUNT) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact minor-unit range.");
    }
    if (decimalPointIndex >= 0 && fractionalText.isEmpty()) {
      throw new IllegalArgumentException("Money amount must not end with a decimal point.");
    }
    requireDigitsOrEmpty(fractionalText, "Money amount must contain decimal digits only.");
    int scale = currencyUnit.minorUnitScale();
    if (scale == 0 && decimalPointIndex >= 0) {
      throw new IllegalArgumentException(
          "Money amount for " + currencyUnit.code() + " must not contain fractional digits.");
    }
    if (fractionalText.length() > scale) {
      throw new IllegalArgumentException(
          "Money amount for "
              + currencyUnit.code()
              + " must use at most "
              + scale
              + " fractional digits.");
    }
    long wholeUnits = parseExactLong(wholeUnitsText);
    long minorUnits;
    try {
      minorUnits = Math.multiplyExact(wholeUnits, powerOfTen(scale));
      if (!fractionalText.isEmpty()) {
        String paddedFractionalText = fractionalText + "0".repeat(scale - fractionalText.length());
        minorUnits = Math.addExact(minorUnits, parseExactLong(paddedFractionalText));
      }
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact minor-unit range.", exception);
    }
    return new Money(currencyUnit, minorUnits);
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
    int scale = scale();
    if (scale == 0) {
      return Long.toString(minorUnits);
    }
    long divisor = powerOfTen(scale);
    long wholeUnits = minorUnits / divisor;
    long fractionalUnits = minorUnits % divisor;
    return wholeUnits + "." + leftPadFraction(fractionalUnits, scale);
  }

  /** Adds two money values in the same currency unit. */
  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(currencyUnit, Math.addExact(minorUnits, other.minorUnits));
  }

  /** Subtracts one smaller or equal money value from this value. */
  public Money minus(Money other) {
    requireSameCurrency(other);
    if (minorUnits < other.minorUnits) {
      throw new IllegalArgumentException("Money subtraction would produce a negative result.");
    }
    return new Money(currencyUnit, Math.subtractExact(minorUnits, other.minorUnits));
  }

  @Override
  public int compareTo(Money other) {
    requireSameCurrency(other);
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

  private void requireSameCurrency(Money other) {
    Objects.requireNonNull(other, "other");
    if (!currencyUnit.equals(other.currencyUnit)) {
      throw new IllegalArgumentException("Money values must share one currency unit.");
    }
  }

  private static long parseExactLong(String digits) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact minor-unit range.", exception);
    }
  }

  private static void requireDigits(String text, String message) {
    if (text.isEmpty()
        || !text.chars().allMatch(character -> character >= '0' && character <= '9')) {
      throw new IllegalArgumentException(message);
    }
  }

  private static void requireDigitsOrEmpty(String text, String message) {
    if (!text.isEmpty()
        && !text.chars().allMatch(character -> character >= '0' && character <= '9')) {
      throw new IllegalArgumentException(message);
    }
  }

  private static long powerOfTen(int exponent) {
    long value = 1L;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }

  private static String leftPadFraction(long fractionalUnits, int scale) {
    String digits = Long.toString(fractionalUnits);
    if (digits.length() == scale) {
      return digits;
    }
    return "0".repeat(scale - digits.length()) + digits;
  }
}
