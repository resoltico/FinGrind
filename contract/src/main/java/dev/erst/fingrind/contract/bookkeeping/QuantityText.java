package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.Objects;

/** Scale-free exact quantity text later resolved against one inventory account unit of measure. */
public record QuantityText(String value) {
  /** Validates and canonicalizes one non-negative plain-decimal quantity string. */
  public QuantityText {
    Objects.requireNonNull(value, "value");
    value = canonicalize(value);
  }

  /** Resolves this quantity text through one inventory-account unit of measure. */
  public Quantity resolve(UnitOfMeasure unitOfMeasure) {
    Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    return unitOfMeasure.parseQuantity(value);
  }

  /** Returns whether this quantity text is exactly zero. */
  public boolean isZero() {
    return "0".equals(value);
  }

  private static String canonicalize(String value) {
    requireCanonicalSurface(value);
    QuantityTextParts parts = splitParts(value);
    validateWholeUnitsText(parts.wholeUnitsText());
    validateFractionalText(parts.fractionalText(), parts.hasDecimalPoint());
    return canonicalQuantityText(parts.wholeUnitsText(), parts.fractionalText());
  }

  private static void requireCanonicalSurface(String value) {
    if (!value.equals(value.strip())) {
      throw new IllegalArgumentException(
          "Quantity text must not contain leading or trailing space.");
    }
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Quantity text must not be blank.");
    }
    if (value.startsWith("+") || value.startsWith("-")) {
      throw new IllegalArgumentException("Quantity text must be non-negative and unsigned.");
    }
    if (value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
      throw new IllegalArgumentException(
          "Quantity text must be one plain decimal string without exponent notation.");
    }
  }

  private static QuantityTextParts splitParts(String value) {
    int decimalPointIndex = value.indexOf('.');
    if (decimalPointIndex != value.lastIndexOf('.')) {
      throw new IllegalArgumentException("Quantity text must contain at most one decimal point.");
    }
    return new QuantityTextParts(
        decimalPointIndex >= 0 ? value.substring(0, decimalPointIndex) : value,
        decimalPointIndex >= 0 ? value.substring(decimalPointIndex + 1) : "",
        decimalPointIndex >= 0);
  }

  private static void validateWholeUnitsText(String wholeUnitsText) {
    requireDigits(wholeUnitsText, "Quantity text must contain decimal digits.");
    if (wholeUnitsText.length() > 1 && wholeUnitsText.startsWith("0")) {
      throw new IllegalArgumentException(
          "Quantity text must not contain redundant leading zeroes.");
    }
    if (wholeUnitsText.length() > Quantity.maxScaledUnitsDigitCount()) {
      throw new IllegalArgumentException("Quantity text is outside the supported exact range.");
    }
  }

  private static void validateFractionalText(String fractionalText, boolean hasDecimalPoint) {
    if (hasDecimalPoint && fractionalText.isEmpty()) {
      throw new IllegalArgumentException("Quantity text must not end with a decimal point.");
    }
    requireDigitsOrEmpty(fractionalText, "Quantity text must contain decimal digits only.");
    if (fractionalText.length() > Quantity.maxSupportedScale()) {
      throw new IllegalArgumentException(
          "Quantity text must use at most " + Quantity.maxSupportedScale() + " fractional digits.");
    }
  }

  private static String canonicalQuantityText(String wholeUnitsText, String fractionalText) {
    if (fractionalText.isEmpty()) {
      return wholeUnitsText;
    }
    int lastNonZero = fractionalText.length() - 1;
    while (lastNonZero >= 0 && fractionalText.charAt(lastNonZero) == '0') {
      lastNonZero--;
    }
    if (lastNonZero < 0) {
      return wholeUnitsText;
    }
    return wholeUnitsText + "." + fractionalText.substring(0, lastNonZero + 1);
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

  private record QuantityTextParts(
      String wholeUnitsText, String fractionalText, boolean hasDecimalPoint) {}
}
