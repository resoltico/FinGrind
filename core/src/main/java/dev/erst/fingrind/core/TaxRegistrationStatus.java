package dev.erst.fingrind.core;

import java.util.List;

/** Canonical tax-registration status for one accounting entity profile. */
public enum TaxRegistrationStatus implements WireValue {
  REGISTERED,
  NOT_REGISTERED,
  UNSPECIFIED;

  @Override
  public String wireValue() {
    return switch (this) {
      case REGISTERED -> "REGISTERED";
      case NOT_REGISTERED -> "NOT_REGISTERED";
      case UNSPECIFIED -> "UNSPECIFIED";
    };
  }

  /** Returns every stable tax-registration wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxRegistrationStatus.class);
  }

  /** Parses one stable tax-registration wire value. */
  public static TaxRegistrationStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxRegistrationStatus.class, wireValue, "Unsupported taxRegistrationStatus");
  }
}
