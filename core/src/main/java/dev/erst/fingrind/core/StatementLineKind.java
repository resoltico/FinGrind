package dev.erst.fingrind.core;

import java.util.List;

/** Canonical origin of one public statement line. */
public enum StatementLineKind implements WireValue {
  DECLARED_ACCOUNT,
  CURRENT_PERIOD_RESULT;

  /** Returns the stable public wire value for this line kind. */
  @Override
  public String wireValue() {
    return switch (this) {
      case DECLARED_ACCOUNT -> "DECLARED_ACCOUNT";
      case CURRENT_PERIOD_RESULT -> "CURRENT_PERIOD_RESULT";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(StatementLineKind.class);
  }

  /** Parses one stable public wire value. */
  public static StatementLineKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        StatementLineKind.class, wireValue, "Unsupported statementLineKind");
  }
}
