package dev.erst.fingrind.core;

import java.util.function.IntFunction;

/** Shared canonical text parsing and formatting helpers for exact non-negative scaled decimals. */
final class ExactDecimalTextSupport {
  private static final int MAX_SCALED_INTEGER_DIGIT_COUNT = Long.toString(Long.MAX_VALUE).length();

  private ExactDecimalTextSupport() {}

  static int maxScaledIntegerDigitCount() {
    return MAX_SCALED_INTEGER_DIGIT_COUNT;
  }

  static int requireSupportedDecimalText(String valueName, String decimalText) {
    if (!decimalText.equals(decimalText.strip())) {
      throw new IllegalArgumentException(
          valueName + " must not contain leading or trailing space.");
    }
    if (decimalText.isEmpty()) {
      throw new IllegalArgumentException(valueName + " must not be blank.");
    }
    if (decimalText.startsWith("+") || decimalText.startsWith("-")) {
      throw new IllegalArgumentException(valueName + " must be non-negative and unsigned.");
    }
    if (decimalText.indexOf('e') >= 0 || decimalText.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          valueName + " must be a plain decimal string without exponent notation.");
    }
    int decimalPointIndex = decimalText.indexOf('.');
    if (decimalPointIndex != decimalText.lastIndexOf('.')) {
      throw new IllegalArgumentException(valueName + " must contain at most one decimal point.");
    }
    return decimalPointIndex;
  }

  static DecimalParts splitDecimalText(String decimalText, int decimalPointIndex) {
    String wholeUnitsText =
        decimalPointIndex >= 0 ? decimalText.substring(0, decimalPointIndex) : decimalText;
    String fractionalText =
        decimalPointIndex >= 0 ? decimalText.substring(decimalPointIndex + 1) : "";
    return new DecimalParts(wholeUnitsText, fractionalText);
  }

  static void validateWholeUnits(String valueName, String wholeUnitsText, String rangeMessage) {
    requireDigits(wholeUnitsText, valueName + " must contain decimal digits.");
    if (wholeUnitsText.length() > 1 && wholeUnitsText.startsWith("0")) {
      throw new IllegalArgumentException(valueName + " must not contain redundant leading zeroes.");
    }
    if (wholeUnitsText.length() > MAX_SCALED_INTEGER_DIGIT_COUNT) {
      throw new IllegalArgumentException(rangeMessage);
    }
  }

  static void validateFractionalText(
      String valueName,
      String fractionalText,
      boolean hasDecimalPoint,
      int scale,
      String scaleZeroMessage,
      IntFunction<String> tooManyFractionalDigitsMessage) {
    if (hasDecimalPoint && fractionalText.isEmpty()) {
      throw new IllegalArgumentException(valueName + " must not end with a decimal point.");
    }
    requireDigitsOrEmpty(fractionalText, valueName + " must contain decimal digits only.");
    if (scale == 0 && hasDecimalPoint) {
      throw new IllegalArgumentException(scaleZeroMessage);
    }
    if (fractionalText.length() > scale) {
      throw new IllegalArgumentException(tooManyFractionalDigitsMessage.apply(scale));
    }
  }

  static long toScaledUnits(int scale, DecimalParts parts, String rangeMessage) {
    long wholeUnits = parseExactLong(parts.wholeUnitsText(), rangeMessage);
    try {
      long scaledUnits = Math.multiplyExact(wholeUnits, powerOfTen(scale));
      if (parts.fractionalText().isEmpty()) {
        return scaledUnits;
      }
      String paddedFractionalText =
          parts.fractionalText() + "0".repeat(scale - parts.fractionalText().length());
      return Math.addExact(scaledUnits, parseExactLong(paddedFractionalText, rangeMessage));
    } catch (ArithmeticException exception) {
      throw new IllegalArgumentException(rangeMessage, exception);
    }
  }

  static String canonicalDecimal(long scaledUnits, int scale) {
    if (scale == 0) {
      return Long.toString(scaledUnits);
    }
    long divisor = powerOfTen(scale);
    long wholeUnits = scaledUnits / divisor;
    long fractionalUnits = scaledUnits % divisor;
    return wholeUnits + "." + leftPadFraction(fractionalUnits, scale);
  }

  static long powerOfTen(int exponent) {
    long value = 1L;
    for (int index = 0; index < exponent; index++) {
      value = Math.multiplyExact(value, 10L);
    }
    return value;
  }

  private static long parseExactLong(String digits, String rangeMessage) {
    try {
      return Long.parseLong(digits);
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException(rangeMessage, exception);
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

  private static String leftPadFraction(long fractionalUnits, int scale) {
    String digits = Long.toString(fractionalUnits);
    if (digits.length() == scale) {
      return digits;
    }
    return "0".repeat(scale - digits.length()) + digits;
  }

  record DecimalParts(String wholeUnitsText, String fractionalText) {}
}
