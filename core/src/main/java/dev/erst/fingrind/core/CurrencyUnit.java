package dev.erst.fingrind.core;

import java.util.Locale;
import java.util.Objects;

/**
 * One supported ISO currency unit from FinGrind's pinned registry with its exact minor-unit scale.
 */
public final class CurrencyUnit {
  private static final int MAX_SUPPORTED_MINOR_UNIT_SCALE = 9;

  private final String code;
  private final int minorUnitScale;

  private CurrencyUnit(String code, int minorUnitScale) {
    this.code = code;
    this.minorUnitScale = minorUnitScale;
  }

  /**
   * Resolves one supported ISO currency unit from user or contract text against the pinned
   * registry.
   */
  public static CurrencyUnit of(String codeText) {
    Objects.requireNonNull(codeText, "codeText");
    if (!codeText.equals(codeText.strip())) {
      throw new IllegalArgumentException(
          "Currency unit code must not contain leading or trailing space.");
    }
    if (!codeText.equals(codeText.toUpperCase(Locale.ROOT)) || !codeText.matches("[A-Z]{3}")) {
      throw new IllegalArgumentException(
          "Currency unit code must be one canonical three-letter uppercase ISO 4217 code.");
    }
    int scale =
        CurrencyUnitRegistry.findMinorUnitScale(codeText)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Unsupported currency unit code: " + codeText + "."));
    return new CurrencyUnit(codeText, scale);
  }

  /** Returns the largest exact minor-unit scale accepted by the FinGrind currency registry. */
  public static int maxSupportedMinorUnitScale() {
    return MAX_SUPPORTED_MINOR_UNIT_SCALE;
  }

  /** Returns the stable ISO 4217 currency code. */
  public String code() {
    return code;
  }

  /** Returns the exact minor-unit scale accepted for posted money in this currency. */
  public int minorUnitScale() {
    return minorUnitScale;
  }

  @Override
  public boolean equals(Object other) {
    return this == other || (other instanceof CurrencyUnit that && code.equals(that.code));
  }

  @Override
  public int hashCode() {
    return code.hashCode();
  }

  @Override
  public String toString() {
    return "CurrencyUnit[code=" + code + ", minorUnitScale=" + minorUnitScale + "]";
  }
}
