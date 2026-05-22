package dev.erst.fingrind.core;

import java.util.List;

/** Canonical caller-authored entry kinds supported by the public bookkeeping write surface. */
public enum BookkeepingEntryKind implements WireValue {
  CASH_REVENUE,
  CASH_EXPENSE,
  OWNER_CONTRIBUTION,
  OWNER_DRAW,
  MANUAL_ADJUSTMENT;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH_REVENUE -> "CASH_REVENUE";
      case CASH_EXPENSE -> "CASH_EXPENSE";
      case OWNER_CONTRIBUTION -> "OWNER_CONTRIBUTION";
      case OWNER_DRAW -> "OWNER_DRAW";
      case MANUAL_ADJUSTMENT -> "MANUAL_ADJUSTMENT";
    };
  }

  /** Returns every stable public bookkeeping entry kind in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(BookkeepingEntryKind.class);
  }

  /** Parses one stable public bookkeeping entry kind. */
  public static BookkeepingEntryKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        BookkeepingEntryKind.class, wireValue, "Unsupported bookkeeping entry kind");
  }
}
