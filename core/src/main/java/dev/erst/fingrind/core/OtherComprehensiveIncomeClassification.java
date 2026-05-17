package dev.erst.fingrind.core;

import java.util.List;

/** Canonical OCI taxonomy for comprehensive-income reporting. */
public enum OtherComprehensiveIncomeClassification implements WireValue {
  FOREIGN_CURRENCY_TRANSLATION,
  ASSET_REVALUATION,
  CASH_FLOW_HEDGE_RESERVE,
  FAIR_VALUE_RESERVE,
  ACTUARIAL_REMEASUREMENT,
  OTHER;

  @Override
  public String wireValue() {
    return switch (this) {
      case FOREIGN_CURRENCY_TRANSLATION -> "FOREIGN_CURRENCY_TRANSLATION";
      case ASSET_REVALUATION -> "ASSET_REVALUATION";
      case CASH_FLOW_HEDGE_RESERVE -> "CASH_FLOW_HEDGE_RESERVE";
      case FAIR_VALUE_RESERVE -> "FAIR_VALUE_RESERVE";
      case ACTUARIAL_REMEASUREMENT -> "ACTUARIAL_REMEASUREMENT";
      case OTHER -> "OTHER";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(OtherComprehensiveIncomeClassification.class);
  }

  /** Parses one stable public wire value. */
  public static OtherComprehensiveIncomeClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        OtherComprehensiveIncomeClassification.class,
        wireValue,
        "Unsupported otherComprehensiveIncomeClassification");
  }
}
