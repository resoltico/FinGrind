package dev.erst.fingrind.core;

import java.util.List;

/** Canonical section taxonomy for the built-in cash-flow statement. */
public enum CashFlowSectionKind implements WireValue {
  OPERATING,
  INVESTING,
  FINANCING;

  /** Returns the stable public wire value for this section kind. */
  @Override
  public String wireValue() {
    return switch (this) {
      case OPERATING -> "OPERATING";
      case INVESTING -> "INVESTING";
      case FINANCING -> "FINANCING";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CashFlowSectionKind.class);
  }

  /** Parses one stable public wire value. */
  public static CashFlowSectionKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CashFlowSectionKind.class, wireValue, "Unsupported cashFlowSectionKind");
  }
}
