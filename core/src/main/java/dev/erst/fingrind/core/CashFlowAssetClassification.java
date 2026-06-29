package dev.erst.fingrind.core;

import java.util.List;

/** Declared cash-flow role for one ASSET account inside the chart taxonomy. */
public enum CashFlowAssetClassification implements WireValue {
  CASH_AND_CASH_EQUIVALENT,
  NON_CASH;

  /** Returns whether this declared asset participates in cash and cash equivalents. */
  public boolean cashAndCashEquivalent() {
    return switch (this) {
      case CASH_AND_CASH_EQUIVALENT -> true;
      case NON_CASH -> false;
    };
  }

  /** Returns the stable public wire value for this classification. */
  @Override
  public String wireValue() {
    return switch (this) {
      case CASH_AND_CASH_EQUIVALENT -> "CASH_AND_CASH_EQUIVALENT";
      case NON_CASH -> "NON_CASH";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(CashFlowAssetClassification.class);
  }

  /** Parses one stable public wire value. */
  public static CashFlowAssetClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        CashFlowAssetClassification.class, wireValue, "Unsupported cashFlowAssetClassification");
  }
}
