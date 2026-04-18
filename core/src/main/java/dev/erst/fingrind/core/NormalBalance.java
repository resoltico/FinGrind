package dev.erst.fingrind.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Side of the journal equation that increases one declared account. */
public enum NormalBalance {
  DEBIT,
  CREDIT;

  /** Returns the stable public wire value for this normal-balance side. */
  public String wireValue() {
    return switch (this) {
      case DEBIT -> "DEBIT";
      case CREDIT -> "CREDIT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return Arrays.stream(values()).map(NormalBalance::wireValue).toList();
  }

  /** Parses one stable public wire value. */
  public static NormalBalance fromWireValue(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    return Arrays.stream(values())
        .filter(value -> value.wireValue().equals(wireValue))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported normalBalance: " + wireValue));
  }
}
