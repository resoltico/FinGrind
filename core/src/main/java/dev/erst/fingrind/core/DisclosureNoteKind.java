package dev.erst.fingrind.core;

import java.util.List;

/** Canonical disclosure-note taxonomy for externalized reporting support. */
public enum DisclosureNoteKind implements WireValue {
  BASIS_OF_PREPARATION,
  ACCOUNTING_POLICIES,
  TAX_POSITION,
  FOREIGN_EXCHANGE_EXPOSURE,
  RECEIVABLES_AND_PAYABLES,
  INVENTORY_VALUATION,
  OWNER_AND_EQUITY_MOVEMENTS,
  RELATED_PARTIES,
  SUBSEQUENT_EVENTS,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case BASIS_OF_PREPARATION -> "BASIS_OF_PREPARATION";
      case ACCOUNTING_POLICIES -> "ACCOUNTING_POLICIES";
      case TAX_POSITION -> "TAX_POSITION";
      case FOREIGN_EXCHANGE_EXPOSURE -> "FOREIGN_EXCHANGE_EXPOSURE";
      case RECEIVABLES_AND_PAYABLES -> "RECEIVABLES_AND_PAYABLES";
      case INVENTORY_VALUATION -> "INVENTORY_VALUATION";
      case OWNER_AND_EQUITY_MOVEMENTS -> "OWNER_AND_EQUITY_MOVEMENTS";
      case RELATED_PARTIES -> "RELATED_PARTIES";
      case SUBSEQUENT_EVENTS -> "SUBSEQUENT_EVENTS";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(DisclosureNoteKind.class);
  }

  /** Parses one stable public wire value. */
  public static DisclosureNoteKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        DisclosureNoteKind.class, wireValue, "Unsupported disclosureNoteKind");
  }
}
