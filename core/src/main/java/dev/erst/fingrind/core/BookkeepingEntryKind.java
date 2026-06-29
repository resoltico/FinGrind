package dev.erst.fingrind.core;

import java.util.List;

/** Canonical caller-authored entry kinds supported by the public bookkeeping write surface. */
public enum BookkeepingEntryKind implements WireValue {
  DIRECT_JOURNAL,
  SALE,
  EXPENSE,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL;

  @Override
  public String wireValue() {
    return switch (this) {
      case DIRECT_JOURNAL -> "DIRECT_JOURNAL";
      case SALE -> "SALE";
      case EXPENSE -> "EXPENSE";
      case OWNER_CONTRIBUTION -> "OWNER_CONTRIBUTION";
      case OWNER_WITHDRAWAL -> "OWNER_WITHDRAWAL";
      case OPENING_POSITION -> "OPENING_POSITION";
      case REVERSAL -> "REVERSAL";
    };
  }

  /** Returns one lowercase narrative label for text guidance and rejection language. */
  public String narrativeLabel() {
    return switch (this) {
      case DIRECT_JOURNAL -> "direct journal";
      case SALE -> "sale";
      case EXPENSE -> "expense";
      case OWNER_CONTRIBUTION -> "owner contribution";
      case OWNER_WITHDRAWAL -> "owner withdrawal";
      case OPENING_POSITION -> "opening position";
      case REVERSAL -> "reversal";
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
