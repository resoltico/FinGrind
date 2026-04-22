package dev.erst.fingrind.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    return Arrays.stream(values()).map(BalanceSide::wireValue).toList();
  }

  /** Parses one stable public wire value. */
  public static BalanceSide fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported balanceSide: " + wireValue));
  }
}
