package dev.erst.fingrind.core;

import java.util.List;

/** Side of the journal equation that increases one declared account. */
public enum NormalBalance implements WireValue {
  DEBIT,
  CREDIT;

  /** Returns the stable public wire value for this normal-balance side. */
  @Override
  public String wireValue() {
    return switch (this) {
      case DEBIT -> "DEBIT";
      case CREDIT -> "CREDIT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(NormalBalance.class);
  }

  /** Parses one stable public wire value. */
  public static NormalBalance fromWireValue(String wireValue) {
    return WireValue.fromWireValue(NormalBalance.class, wireValue, "Unsupported normalBalance");
  }
}
