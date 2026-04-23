package dev.erst.fingrind.core;

import java.util.List;

/** Side of a computed net balance, including the balanced zero state. */
public enum BalanceSide implements WireValue {
  DEBIT,
  CREDIT,
  ZERO;

  /** Returns the stable public wire value for this balance side. */
  @Override
  public String wireValue() {
    return switch (this) {
      case DEBIT -> "DEBIT";
      case CREDIT -> "CREDIT";
      case ZERO -> "ZERO";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BalanceSide.class);
  }

  /** Parses one stable public wire value. */
  public static BalanceSide fromWireValue(String wireValue) {
    return WireValue.fromWireValue(BalanceSide.class, wireValue, "Unsupported balanceSide");
  }
}
