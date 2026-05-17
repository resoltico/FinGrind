package dev.erst.fingrind.core;

import java.util.List;

/** Canonical gross/net pricing posture for one tax code or event. */
public enum TaxPricingMode implements WireValue {
  EXCLUSIVE,
  INCLUSIVE;

  @Override
  public String wireValue() {
    return switch (this) {
      case EXCLUSIVE -> "EXCLUSIVE";
      case INCLUSIVE -> "INCLUSIVE";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxPricingMode.class);
  }

  /** Parses one stable public wire value. */
  public static TaxPricingMode fromWireValue(String wireValue) {
    return WireValue.fromWireValue(TaxPricingMode.class, wireValue, "Unsupported taxPricingMode");
  }
}
