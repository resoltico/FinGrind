package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical text parsing and formatting helpers for exact money values. */
final class MoneyTextSupport {
  private static final int MAX_MINOR_UNITS_DIGIT_COUNT = Long.toString(Long.MAX_VALUE).length();

  private MoneyTextSupport() {}

  static int maxMinorUnitsDigitCount() {
    return MAX_MINOR_UNITS_DIGIT_COUNT;
  }

  static Money parse(CurrencyUnit currencyUnit, String amountText) {
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    Objects.requireNonNull(amountText, "amountText");
    int decimalPointIndex = requireSupportedAmountText(amountText);
    MoneyAmountParts parts = splitAmountText(amountText, decimalPointIndex);
    validateWholeUnits(parts.wholeUnitsText());
    validateFractionalText(currencyUnit, parts, decimalPointIndex >= 0);
    return Money.ofMinorUnits(currencyUnit, toMinorUnits(currencyUnit, parts));
  }

  static String canonicalDecimal(long minorUnits, int scale) {
    if (scale == 0) {
      return Long.toString(minorUnits);
    }
    long divisor = powerOfTen(scale);
    long wholeUnits = minorUnits / divisor;
    long fractionalUnits = minorUnits % divisor;
    return wholeUnits + "." + leftPadFraction(fractionalUnits, scale);
  }

  private static int requireSupportedAmountText(String amountText) {
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
    return decimalPointIndex;
  }

  private static MoneyAmountParts splitAmountText(String amountText, int decimalPointIndex) {
    String wholeUnitsText =
        decimalPointIndex >= 0 ? amountText.substring(0, decimalPointIndex) : amountText;
    String fractionalText =
        decimalPointIndex >= 0 ? amountText.substring(decimalPointIndex + 1) : "";
    return new MoneyAmountParts(wholeUnitsText, fractionalText);
  }

  private static void validateWholeUnits(String wholeUnitsText) {
    requireDigits(wholeUnitsText, "Money amount must contain decimal digits.");
    if (wholeUnitsText.length() > 1 && wholeUnitsText.startsWith("0")) {
      throw new IllegalArgumentException("Money amount must not contain redundant leading zeroes.");
    }
    if (wholeUnitsText.length() > MAX_MINOR_UNITS_DIGIT_COUNT) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact minor-unit range.");
    }
  }

  private static void validateFractionalText(
      CurrencyUnit currencyUnit, MoneyAmountParts parts, boolean hasDecimalPoint) {
    if (hasDecimalPoint && parts.fractionalText().isEmpty()) {
      throw new IllegalArgumentException("Money amount must not end with a decimal point.");
    }
    requireDigitsOrEmpty(parts.fractionalText(), "Money amount must contain decimal digits only.");
    int scale = currencyUnit.minorUnitScale();
    if (scale == 0 && hasDecimalPoint) {
      throw new IllegalArgumentException(
          "Money amount for " + currencyUnit.code() + " must not contain fractional digits.");
    }
    if (parts.fractionalText().length() > scale) {
      throw new IllegalArgumentException(
          "Money amount for "
              + currencyUnit.code()
              + " must use at most "
              + scale
              + " fractional digits.");
    }
  }

  private static long toMinorUnits(CurrencyUnit currencyUnit, MoneyAmountParts parts) {
    long wholeUnits = parseExactLong(parts.wholeUnitsText());
    int scale = currencyUnit.minorUnitScale();
    try {
      long minorUnits = Math.multiplyExact(wholeUnits, powerOfTen(scale));
      if (parts.fractionalText().isEmpty()) {
        return minorUnits;
      }
      String paddedFractionalText =
          parts.fractionalText() + "0".repeat(scale - parts.fractionalText().length());
      return Math.addExact(minorUnits, parseExactLong(paddedFractionalText));
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(
          "Money amount is outside the supported exact minor-unit range.", exception);
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

  private record MoneyAmountParts(String wholeUnitsText, String fractionalText) {}
}
