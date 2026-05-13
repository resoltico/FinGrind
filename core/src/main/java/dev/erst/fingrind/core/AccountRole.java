package dev.erst.fingrind.core;

import java.util.List;

/** Canonical doctrinal role for one declared account inside the current FinGrind chart. */
public enum AccountRole implements WireValue {
  ORDINARY,
  CONTRA,
  RETAINED_EARNINGS;

  /** Returns the stable public wire value for this account role. */
  @Override
  public String wireValue() {
    return switch (this) {
      case ORDINARY -> "ORDINARY";
      case CONTRA -> "CONTRA";
      case RETAINED_EARNINGS -> "RETAINED_EARNINGS";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountRole.class);
  }

  /** Parses one stable public wire value. */
  public static AccountRole fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountRole.class, wireValue, "Unsupported accountRole");
  }
}
