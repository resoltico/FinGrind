package dev.erst.fingrind.core;

import java.util.List;

/** Canonical role vocabulary for external parties referenced by accounting events. */
public enum CounterpartyKind implements WireValue {
  CUSTOMER,
  SUPPLIER,
  EMPLOYEE,
  OWNER,
  GOVERNMENT_AUTHORITY,
  BANK,
  PARTNER,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case CUSTOMER -> "CUSTOMER";
      case SUPPLIER -> "SUPPLIER";
      case EMPLOYEE -> "EMPLOYEE";
      case OWNER -> "OWNER";
      case GOVERNMENT_AUTHORITY -> "GOVERNMENT_AUTHORITY";
      case BANK -> "BANK";
      case PARTNER -> "PARTNER";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CounterpartyKind.class);
  }

  /** Parses one stable public wire value. */
  public static CounterpartyKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CounterpartyKind.class, wireValue, "Unsupported counterpartyKind");
  }
}
