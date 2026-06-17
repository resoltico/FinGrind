package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.WireValue;
import java.util.List;

/**
 * Stable recipe vocabulary for journal-backed business-event helpers on the public write surface.
 */
public enum JournalRecipeKind implements WireValue {
  CASH_REVENUE,
  CASH_EXPENSE,
  EQUITY_CONTRIBUTION,
  EQUITY_WITHDRAWAL;

  @Override
  public String wireValue() {
    return switch (this) {
      case CASH_REVENUE -> "CASH_REVENUE";
      case CASH_EXPENSE -> "CASH_EXPENSE";
      case EQUITY_CONTRIBUTION -> "EQUITY_CONTRIBUTION";
      case EQUITY_WITHDRAWAL -> "EQUITY_WITHDRAWAL";
    };
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(JournalRecipeKind.class);
  }

  /** Parses one stable recipe wire value. */
  public static JournalRecipeKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        JournalRecipeKind.class, wireValue, "Unsupported journal recipe kind");
  }
}
