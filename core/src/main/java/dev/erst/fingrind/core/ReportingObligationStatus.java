package dev.erst.fingrind.core;

import java.util.List;

/** Canonical reporting-obligation status for one accounting entity profile. */
public enum ReportingObligationStatus implements WireValue {
  INTERNAL_MANAGEMENT_ONLY,
  BASIC_STANDARD_REPORTING,
  EXTERNAL_COMPLIANCE_PACK_REQUIRED,
  UNSPECIFIED;

  @Override
  public String wireValue() {
    return switch (this) {
      case INTERNAL_MANAGEMENT_ONLY -> "INTERNAL_MANAGEMENT_ONLY";
      case BASIC_STANDARD_REPORTING -> "BASIC_STANDARD_REPORTING";
      case EXTERNAL_COMPLIANCE_PACK_REQUIRED -> "EXTERNAL_COMPLIANCE_PACK_REQUIRED";
      case UNSPECIFIED -> "UNSPECIFIED";
    };
  }

  /** Returns every stable reporting-obligation wire value. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ReportingObligationStatus.class);
  }

  /** Parses one stable reporting-obligation wire value. */
  public static ReportingObligationStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ReportingObligationStatus.class, wireValue, "Unsupported reportingObligationStatus");
  }
}
