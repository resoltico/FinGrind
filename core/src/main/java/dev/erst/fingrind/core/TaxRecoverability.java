package dev.erst.fingrind.core;

import java.util.List;

/** Canonical input-tax recoverability posture. */
public enum TaxRecoverability implements WireValue {
  FULLY_RECOVERABLE,
  PARTIALLY_RECOVERABLE,
  NON_RECOVERABLE;

  @Override
  public String wireValue() {
    return switch (this) {
      case FULLY_RECOVERABLE -> "FULLY_RECOVERABLE";
      case PARTIALLY_RECOVERABLE -> "PARTIALLY_RECOVERABLE";
      case NON_RECOVERABLE -> "NON_RECOVERABLE";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(TaxRecoverability.class);
  }

  /** Parses one stable public wire value. */
  public static TaxRecoverability fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        TaxRecoverability.class, wireValue, "Unsupported taxRecoverability");
  }
}
