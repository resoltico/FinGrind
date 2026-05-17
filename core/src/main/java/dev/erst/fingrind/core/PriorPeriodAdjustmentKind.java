package dev.erst.fingrind.core;

import java.util.List;

/** Canonical reason vocabulary for prior-period adjustments. */
public enum PriorPeriodAdjustmentKind implements WireValue {
  OPENING_BALANCE_CORRECTION,
  IMPORT_RECONCILIATION,
  DISCOVERED_ERROR,
  POLICY_RESTATEMENT,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case OPENING_BALANCE_CORRECTION -> "OPENING_BALANCE_CORRECTION";
      case IMPORT_RECONCILIATION -> "IMPORT_RECONCILIATION";
      case DISCOVERED_ERROR -> "DISCOVERED_ERROR";
      case POLICY_RESTATEMENT -> "POLICY_RESTATEMENT";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PriorPeriodAdjustmentKind.class);
  }

  /** Parses one stable public wire value. */
  public static PriorPeriodAdjustmentKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PriorPeriodAdjustmentKind.class, wireValue, "Unsupported priorPeriodAdjustmentKind");
  }
}
