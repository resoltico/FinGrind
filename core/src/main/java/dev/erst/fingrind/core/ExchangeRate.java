package dev.erst.fingrind.core;

import java.util.Objects;

/** Exact positive exchange rate stored as one canonical plain-decimal quote. */
public record ExchangeRate(String value) {
  /** Normalizes and validates one exchange rate. */
  public ExchangeRate {
    Objects.requireNonNull(value, "value");
    if (!value.equals(value.strip())) {
      throw new IllegalArgumentException(
          "Exchange rate must not contain leading or trailing space.");
    }
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Exchange rate must not be blank.");
    }
    if (value.startsWith("+") || value.startsWith("-")) {
      throw new IllegalArgumentException("Exchange rate must be strictly positive and unsigned.");
    }
    if (value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          "Exchange rate must be a plain decimal string without exponent notation.");
    }
    int decimalPointIndex = value.indexOf('.');
    if (decimalPointIndex != value.lastIndexOf('.')) {
      throw new IllegalArgumentException("Exchange rate must contain at most one decimal point.");
    }
    String wholeUnitsText = decimalPointIndex >= 0 ? value.substring(0, decimalPointIndex) : value;
    String fractionalText = decimalPointIndex >= 0 ? value.substring(decimalPointIndex + 1) : "";
    requireDigits(wholeUnitsText, "Exchange rate must contain decimal digits.");
    if (wholeUnitsText.length() > 1 && wholeUnitsText.startsWith("0")) {
      throw new IllegalArgumentException(
          "Exchange rate must not contain redundant leading zeroes.");
    }
    if (decimalPointIndex >= 0 && fractionalText.isEmpty()) {
      throw new IllegalArgumentException("Exchange rate must not end with a decimal point.");
    }
    requireDigitsOrEmpty(fractionalText, "Exchange rate must contain decimal digits only.");
    fractionalText = stripTrailingZeroes(fractionalText);
    value = fractionalText.isEmpty() ? wholeUnitsText : wholeUnitsText + "." + fractionalText;
    if ("0".equals(value)) {
      throw new IllegalArgumentException("Exchange rate must be strictly positive.");
    }
  }

  /** Returns the canonical plain-decimal quote. */
  public String canonicalDecimal() {
    return value;
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

  private static String stripTrailingZeroes(String text) {
    int trimmedLength = text.length();
    while (trimmedLength > 0 && text.charAt(trimmedLength - 1) == '0') {
      trimmedLength--;
    }
    return text.substring(0, trimmedLength);
  }
}
