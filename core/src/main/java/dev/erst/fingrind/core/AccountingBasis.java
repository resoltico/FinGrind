package dev.erst.fingrind.core;

import java.util.List;

/** Canonical recognition basis declared for one book. */
public enum AccountingBasis implements WireValue {
  CASH,
  ACCRUAL,
  HYBRID_POLICY_DEFINED,
  EXTENSION_DEFINED;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH -> "CASH";
      case ACCRUAL -> "ACCRUAL";
      case HYBRID_POLICY_DEFINED -> "HYBRID_POLICY_DEFINED";
      case EXTENSION_DEFINED -> "EXTENSION_DEFINED";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountingBasis.class);
  }

  /** Parses one stable accounting-basis wire value. */
  public static AccountingBasis fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountingBasis.class, wireValue, "Unsupported accountingBasis");
  }
}
