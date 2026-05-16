package dev.erst.fingrind.core;

import java.util.List;

/** Canonical income-statement taxonomy for one declared nominal account. */
public enum ProfitAndLossLineClassification implements WireValue {
  OPERATING_REVENUE,
  OTHER_REVENUE,
  FINANCE_INCOME,
  COST_OF_SALES,
  OPERATING_EXPENSE,
  DEPRECIATION_AND_AMORTIZATION,
  FINANCE_EXPENSE,
  TAX_EXPENSE;

  /** Returns the canonical account type this classification belongs to. */
  public AccountType accountType() {
    return switch (this) {
      case OPERATING_REVENUE, OTHER_REVENUE, FINANCE_INCOME -> AccountType.REVENUE;
      case COST_OF_SALES,
          OPERATING_EXPENSE,
          DEPRECIATION_AND_AMORTIZATION,
          FINANCE_EXPENSE,
          TAX_EXPENSE ->
          AccountType.EXPENSE;
    };
  }

  /** Returns the stable public wire value for this classification. */
  @Override
  public String wireValue() {
    return switch (this) {
      case OPERATING_REVENUE -> "OPERATING_REVENUE";
      case OTHER_REVENUE -> "OTHER_REVENUE";
      case FINANCE_INCOME -> "FINANCE_INCOME";
      case COST_OF_SALES -> "COST_OF_SALES";
      case OPERATING_EXPENSE -> "OPERATING_EXPENSE";
      case DEPRECIATION_AND_AMORTIZATION -> "DEPRECIATION_AND_AMORTIZATION";
      case FINANCE_EXPENSE -> "FINANCE_EXPENSE";
      case TAX_EXPENSE -> "TAX_EXPENSE";
    };
  }

  /** Returns every stable public wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(ProfitAndLossLineClassification.class);
  }

  /** Parses one stable public wire value. */
  public static ProfitAndLossLineClassification fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        ProfitAndLossLineClassification.class,
        wireValue,
        "Unsupported profitAndLossLineClassification");
  }
}
