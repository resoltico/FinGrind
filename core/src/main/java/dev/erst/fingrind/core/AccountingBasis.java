package dev.erst.fingrind.core;

import java.util.List;

/** Canonical accounting-basis posture for one protected book. */
public enum AccountingBasis implements WireValue {
  CASH,
  ACCRUAL;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH -> "CASH";
      case ACCRUAL -> "ACCRUAL";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountingBasis.class);
  }

  /** Parses one stable wire value. */
  public static AccountingBasis fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountingBasis.class, wireValue, "Unsupported accountingBasis");
  }
}
