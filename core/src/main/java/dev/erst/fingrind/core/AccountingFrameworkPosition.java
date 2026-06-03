package dev.erst.fingrind.core;

import java.util.List;

/** Canonical reporting-framework posture for one protected book. */
public enum AccountingFrameworkPosition implements WireValue {
  NON_STATUTORY_INTERNAL_MANAGEMENT;

  @Override
  public String wireValue() {
    return switch (this) {
      case NON_STATUTORY_INTERNAL_MANAGEMENT -> "NON_STATUTORY_INTERNAL_MANAGEMENT";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountingFrameworkPosition.class);
  }

  /** Parses one stable wire value. */
  public static AccountingFrameworkPosition fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        AccountingFrameworkPosition.class, wireValue, "Unsupported accountingFrameworkPosition");
  }
}
