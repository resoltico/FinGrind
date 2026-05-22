package dev.erst.fingrind.core;

import java.util.List;

/** Canonical statement-of-financial-position taxonomy for one declared account. */
public enum FinancialPositionLineClassification implements WireValue {
  CURRENT_ASSET,
  NONCURRENT_ASSET,
  CURRENT_LIABILITY,
  NONCURRENT_LIABILITY,
  CONTRIBUTED_CAPITAL,
  DISTRIBUTIONS,
  ACCUMULATED_RESULT,
  RESERVE,
  OTHER_EQUITY;

  /** Returns the canonical account type this classification belongs to. */
  public AccountType accountType() {
    return switch (this) {
      case CURRENT_ASSET, NONCURRENT_ASSET -> AccountType.ASSET;
      case CURRENT_LIABILITY, NONCURRENT_LIABILITY -> AccountType.LIABILITY;
      case CONTRIBUTED_CAPITAL, DISTRIBUTIONS, ACCUMULATED_RESULT, RESERVE, OTHER_EQUITY ->
          AccountType.EQUITY;
    };
  }

  /** Returns the stable public wire value for this classification. */
  @Override
  public String wireValue() {
    return switch (this) {
      case CURRENT_ASSET -> "CURRENT_ASSET";
      case NONCURRENT_ASSET -> "NONCURRENT_ASSET";
      case CURRENT_LIABILITY -> "CURRENT_LIABILITY";
      case NONCURRENT_LIABILITY -> "NONCURRENT_LIABILITY";
      case CONTRIBUTED_CAPITAL -> "CONTRIBUTED_CAPITAL";
      case DISTRIBUTIONS -> "DISTRIBUTIONS";
      case ACCUMULATED_RESULT -> "ACCUMULATED_RESULT";
      case RESERVE -> "RESERVE";
      case OTHER_EQUITY -> "OTHER_EQUITY";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(FinancialPositionLineClassification.class);
  }

  /** Returns the public wire values that are valid for declared account taxonomy. */
  public static List<String> declaredAccountWireValues() {
    return wireValues();
  }

  /** Parses one stable public wire value. */
  public static FinancialPositionLineClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        FinancialPositionLineClassification.class,
        wireValue,
        "Unsupported financialPositionLineClassification");
  }
}
