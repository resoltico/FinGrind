package dev.erst.fingrind.core;

import java.util.List;

/** Canonical filing-period cadence for one tax registration. */
public enum TaxFilingFrequency implements WireValue {
  MONTHLY,
  QUARTERLY,
  ANNUAL,
  AD_HOC;

  @Override
  public String wireValue() {
    return switch (this) {
      case MONTHLY -> "MONTHLY";
      case QUARTERLY -> "QUARTERLY";
      case ANNUAL -> "ANNUAL";
      case AD_HOC -> "AD_HOC";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxFilingFrequency.class);
  }

  /** Parses one stable public wire value. */
  public static TaxFilingFrequency fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxFilingFrequency.class, wireValue, "Unsupported taxFilingFrequency");
  }
}
