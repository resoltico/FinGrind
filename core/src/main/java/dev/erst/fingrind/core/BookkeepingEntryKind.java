package dev.erst.fingrind.core;

import java.util.List;

/** Canonical caller-authored entry kinds supported by the public bookkeeping write surface. */
public enum BookkeepingEntryKind implements WireValue {
  DIRECT_JOURNAL("direct journal"),
  SALE_SETTLED("settled sale"),
  SALE_ON_CREDIT("sale on credit"),
  PURCHASE_SETTLED("settled purchase"),
  PURCHASE_ON_CREDIT("purchase on credit"),
  EXPENSE_SETTLED("settled expense"),
  EXPENSE_ON_CREDIT("expense on credit"),
  RECEIPT("receipt"),
  PAYMENT("payment"),
  OWNER_CONTRIBUTION("owner contribution"),
  OWNER_WITHDRAWAL("owner withdrawal"),
  OPENING_POSITION("opening position"),
  REVERSAL("reversal");

  private final String narrativeLabel;

  BookkeepingEntryKind(String narrativeLabel) {
    this.narrativeLabel = narrativeLabel;
  }

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns one lowercase narrative label for text guidance and rejection language. */
  public String narrativeLabel() {
    return narrativeLabel;
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
