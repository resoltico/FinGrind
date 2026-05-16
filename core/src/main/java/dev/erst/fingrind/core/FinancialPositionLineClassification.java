package dev.erst.fingrind.core;

import java.util.List;

/** Canonical statement-of-financial-position taxonomy for one declared account or derived line. */
public enum FinancialPositionLineClassification implements WireValue {
  CURRENT_ASSET,
  NONCURRENT_ASSET,
  CURRENT_LIABILITY,
  NONCURRENT_LIABILITY,
  OWNER_CAPITAL,
  OWNER_DRAWINGS,
  PARTNER_CAPITAL,
  PARTNER_CURRENT,
  SHARE_CAPITAL,
  RETAINED_EARNINGS,
  ACCUMULATED_SURPLUS,
  RESERVE,
  CURRENT_PERIOD_RESULT,
  OTHER_EQUITY;

  /** Returns the canonical account type this classification belongs to. */
  public AccountType accountType() {
    return switch (this) {
      case CURRENT_ASSET, NONCURRENT_ASSET -> AccountType.ASSET;
      case CURRENT_LIABILITY, NONCURRENT_LIABILITY -> AccountType.LIABILITY;
      case OWNER_CAPITAL,
          OWNER_DRAWINGS,
          PARTNER_CAPITAL,
          PARTNER_CURRENT,
          SHARE_CAPITAL,
          RETAINED_EARNINGS,
          ACCUMULATED_SURPLUS,
          RESERVE,
          CURRENT_PERIOD_RESULT,
          OTHER_EQUITY ->
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
      case OWNER_CAPITAL -> "OWNER_CAPITAL";
      case OWNER_DRAWINGS -> "OWNER_DRAWINGS";
      case PARTNER_CAPITAL -> "PARTNER_CAPITAL";
      case PARTNER_CURRENT -> "PARTNER_CURRENT";
      case SHARE_CAPITAL -> "SHARE_CAPITAL";
      case RETAINED_EARNINGS -> "RETAINED_EARNINGS";
      case ACCUMULATED_SURPLUS -> "ACCUMULATED_SURPLUS";
      case RESERVE -> "RESERVE";
      case CURRENT_PERIOD_RESULT -> "CURRENT_PERIOD_RESULT";
      case OTHER_EQUITY -> "OTHER_EQUITY";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(FinancialPositionLineClassification.class);
  }

  /** Parses one stable public wire value. */
  public static FinancialPositionLineClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        FinancialPositionLineClassification.class,
        wireValue,
        "Unsupported financialPositionLineClassification");
  }
}
