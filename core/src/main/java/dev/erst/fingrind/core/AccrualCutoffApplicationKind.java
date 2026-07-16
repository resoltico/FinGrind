package dev.erst.fingrind.core;

import java.util.List;

/** Durable application kinds that consume one accrual cut-off's original amount. */
public enum AccrualCutoffApplicationKind implements WireValue {
  RECOGNITION,
  SETTLEMENT;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable application-kind value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccrualCutoffApplicationKind.class);
  }

  /** Parses one stable application-kind value. */
  public static AccrualCutoffApplicationKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        AccrualCutoffApplicationKind.class,
        wireValue,
        "Unsupported accrual cut-off application kind");
  }
}
