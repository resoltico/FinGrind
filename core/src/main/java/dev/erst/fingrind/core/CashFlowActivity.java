package dev.erst.fingrind.core;

import java.util.List;

/** Canonical cash-flow taxonomy for typed events and generated cash-flow reporting. */
public enum CashFlowActivity implements WireValue {
  OPERATING,
  INVESTING,
  FINANCING,
  NONCASH;

  @Override
  public String wireValue() {
    return switch (this) {
      case OPERATING -> "OPERATING";
      case INVESTING -> "INVESTING";
      case FINANCING -> "FINANCING";
      case NONCASH -> "NONCASH";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CashFlowActivity.class);
  }

  /** Parses one stable public wire value. */
  public static CashFlowActivity fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CashFlowActivity.class, wireValue, "Unsupported cashFlowActivity");
  }
}
