package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical text parsing and formatting helpers for exact money values. */
final class MoneyTextSupport {
  private MoneyTextSupport() {}

  static int maxMinorUnitsDigitCount() {
    return ExactDecimalTextSupport.maxScaledIntegerDigitCount();
  }

  static Money parse(CurrencyUnit currencyUnit, String amountText) {
    Objects.requireNonNull(currencyUnit, "currencyUnit");
    Objects.requireNonNull(amountText, "amountText");
    int decimalPointIndex =
        ExactDecimalTextSupport.requireSupportedDecimalText("Money amount", amountText);
    ExactDecimalTextSupport.DecimalParts parts =
        ExactDecimalTextSupport.splitDecimalText(amountText, decimalPointIndex);
    ExactDecimalTextSupport.validateWholeUnits(
        "Money amount",
        parts.wholeUnitsText(),
        "Money amount is outside the supported exact minor-unit range.");
    validateFractionalText(currencyUnit, parts, decimalPointIndex != -1);
    return Money.ofMinorUnits(currencyUnit, toMinorUnits(currencyUnit, parts));
  }

  static String canonicalDecimal(long minorUnits, int scale) {
    return ExactDecimalTextSupport.canonicalDecimal(minorUnits, scale);
  }

  private static void validateFractionalText(
      CurrencyUnit currencyUnit,
      ExactDecimalTextSupport.DecimalParts parts,
      boolean hasDecimalPoint) {
    int scale = currencyUnit.minorUnitScale();
    ExactDecimalTextSupport.validateFractionalText(
        "Money amount",
        parts.fractionalText(),
        hasDecimalPoint,
        scale,
        "Money amount for " + currencyUnit.code() + " must not contain fractional digits.",
        supportedScale ->
            "Money amount for "
                + currencyUnit.code()
                + " must use at most "
                + supportedScale
                + " fractional digits.");
  }

  private static long toMinorUnits(
      CurrencyUnit currencyUnit, ExactDecimalTextSupport.DecimalParts parts) {
    return ExactDecimalTextSupport.toScaledUnits(
        currencyUnit.minorUnitScale(),
        parts,
        "Money amount is outside the supported exact minor-unit range.");
  }
}
