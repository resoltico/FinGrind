package dev.erst.fingrind.core;

import java.util.List;

/** Canonical durable origin kinds preserved for committed postings in one protected book. */
public enum PostingOriginKind implements WireValue {
  DIRECT_JOURNAL,
  SALE_SETTLED,
  SALE_ON_CREDIT,
  PURCHASE_SETTLED,
  PURCHASE_ON_CREDIT,
  INVENTORY_CAPITALIZATION_SETTLED,
  INVENTORY_CAPITALIZATION_ON_CREDIT,
  INVENTORY_WRITE_DOWN,
  INVENTORY_SHRINKAGE,
  INVENTORY_COUNT_INCREASE,
  PREPAYMENT,
  DEFERRED_REVENUE,
  ACCRUED_EXPENSE,
  ACCRUAL_CUTOFF_RECOGNITION,
  ACCRUED_EXPENSE_SETTLEMENT,
  LATVIAN_MONTHLY_PAYROLL,
  LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
  LATVIAN_PAYROLL_STATE_REMITTANCE,
  FIXED_ASSET_CAPITALIZATION,
  FIXED_ASSET_DEPRECIATION,
  FIXED_ASSET_DISPOSAL,
  FINANCING_BORROWING,
  FINANCING_PRINCIPAL_REPAYMENT,
  FINANCING_INTEREST_ACCRUAL,
  FINANCING_INTEREST_PAYMENT,
  FOREIGN_CURRENCY_OBLIGATION,
  REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
  EXPENSE_SETTLED,
  EXPENSE_ON_CREDIT,
  RECEIPT,
  PAYMENT,
  OWNER_CONTRIBUTION,
  OWNER_WITHDRAWAL,
  OPENING_POSITION,
  REVERSAL,
  INTERIM_RESULT_SWEEP,
  FISCAL_YEAR_CLOSE;

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(PostingOriginKind.class);
  }

  /** Parses one stable origin-kind wire value. */
  public static PostingOriginKind fromWireValue(String wireValue) {
    return WireValue.fromWireValue(
        PostingOriginKind.class, wireValue, "Unsupported postingOriginKind");
  }
}
