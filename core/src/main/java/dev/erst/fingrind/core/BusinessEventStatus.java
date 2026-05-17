package dev.erst.fingrind.core;

import java.util.List;

/** Canonical lifecycle status for one persisted business-event subledger fact. */
public enum BusinessEventStatus implements WireValue {
  OPEN,
  POSTED,
  PARTIALLY_SETTLED,
  SETTLED,
  SUPERSEDED;

  @Override
  public String wireValue() {
    return switch (this) {
      case OPEN -> "OPEN";
      case POSTED -> "POSTED";
      case PARTIALLY_SETTLED -> "PARTIALLY_SETTLED";
      case SETTLED -> "SETTLED";
      case SUPERSEDED -> "SUPERSEDED";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BusinessEventStatus.class);
  }

  /** Parses one stable public wire value. */
  public static BusinessEventStatus fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BusinessEventStatus.class, wireValue, "Unsupported businessEventStatus");
  }
}
