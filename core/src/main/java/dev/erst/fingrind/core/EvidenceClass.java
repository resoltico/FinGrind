package dev.erst.fingrind.core;

import java.util.List;

/** Evidence classification carried into resolved-journal semantics. */
public enum EvidenceClass implements WireValue {
  CASH_SETTLEMENT,
  INVOICE,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH_SETTLEMENT -> "CASH_SETTLEMENT";
      case INVOICE -> "INVOICE";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(EvidenceClass.class);
  }

  /** Parses one stable wire value. */
  public static EvidenceClass fromWireValue(String wireValue) {
    return WireValue.fromWireValue(EvidenceClass.class, wireValue, "Unsupported evidenceClass");
  }
}
