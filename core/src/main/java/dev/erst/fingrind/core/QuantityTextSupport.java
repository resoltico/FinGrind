package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical text parsing and formatting helpers for exact quantities. */
final class QuantityTextSupport {
  private QuantityTextSupport() {}

  static int maxScaledUnitsDigitCount() {
    return ExactDecimalTextSupport.maxScaledIntegerDigitCount();
  }

  static Quantity parse(int scale, String quantityText) {
    Quantity.requireSupportedScale(scale);
    Objects.requireNonNull(quantityText, "quantityText");
    int decimalPointIndex =
        ExactDecimalTextSupport.requireSupportedDecimalText("Quantity", quantityText);
    ExactDecimalTextSupport.DecimalParts parts =
        ExactDecimalTextSupport.splitDecimalText(quantityText, decimalPointIndex);
    ExactDecimalTextSupport.validateWholeUnits(
        "Quantity",
        parts.wholeUnitsText(),
        "Quantity is outside the supported exact scaled-unit range.");
    validateFractionalText(scale, parts, decimalPointIndex >= 0);
    return Quantity.ofScaledUnits(scale, toScaledUnits(scale, parts));
  }

  static String canonicalDecimal(long scaledUnits, int scale) {
    Quantity.requireSupportedScale(scale);
    return ExactDecimalTextSupport.canonicalDecimal(scaledUnits, scale);
  }

  private static void validateFractionalText(
      int scale, ExactDecimalTextSupport.DecimalParts parts, boolean hasDecimalPoint) {
    ExactDecimalTextSupport.validateFractionalText(
        "Quantity",
        parts.fractionalText(),
        hasDecimalPoint,
        scale,
        "Quantity must not contain fractional digits at scale 0.",
        supportedScale -> "Quantity must use at most " + supportedScale + " fractional digits.");
  }

  private static long toScaledUnits(int scale, ExactDecimalTextSupport.DecimalParts parts) {
    return ExactDecimalTextSupport.toScaledUnits(
        scale, parts, "Quantity is outside the supported exact scaled-unit range.");
  }
}
