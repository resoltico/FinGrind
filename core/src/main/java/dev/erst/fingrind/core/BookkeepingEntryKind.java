package dev.erst.fingrind.core;

import java.util.List;

/** Canonical caller-authored entry kinds supported by the public bookkeeping write surface. */
public enum BookkeepingEntryKind implements WireValue {
  DIRECT_JOURNAL("direct journal"),
  SALE_SETTLED("settled sale"),
  SALE_ON_CREDIT("sale on credit"),
  PURCHASE_SETTLED("settled purchase"),
  PURCHASE_ON_CREDIT("purchase on credit"),
  INVENTORY_CAPITALIZATION_SETTLED("settled inventory capitalization"),
  INVENTORY_CAPITALIZATION_ON_CREDIT("inventory capitalization on credit"),
  INVENTORY_WRITE_DOWN("inventory write-down"),
  INVENTORY_SHRINKAGE("inventory shrinkage"),
  INVENTORY_COUNT_INCREASE("inventory count increase"),
  PREPAYMENT("prepayment"),
  DEFERRED_REVENUE("deferred revenue"),
  ACCRUED_EXPENSE("accrued expense"),
  ACCRUAL_CUTOFF_RECOGNITION("accrual cut-off recognition"),
  ACCRUED_EXPENSE_SETTLEMENT("accrued-expense settlement"),
  LATVIAN_MONTHLY_PAYROLL("Latvian monthly payroll"),
  LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT("Latvian payroll net-wage settlement"),
  LATVIAN_PAYROLL_STATE_REMITTANCE("Latvian payroll state remittance"),
  FIXED_ASSET_CAPITALIZATION("fixed-asset capitalization"),
  FIXED_ASSET_DEPRECIATION("fixed-asset depreciation"),
  FIXED_ASSET_DISPOSAL("fixed-asset disposal"),
  FINANCING_BORROWING("financing borrowing"),
  FINANCING_PRINCIPAL_REPAYMENT("financing principal repayment"),
  FINANCING_INTEREST_ACCRUAL("financing interest accrual"),
  FINANCING_INTEREST_PAYMENT("financing interest payment"),
  FOREIGN_CURRENCY_OBLIGATION("foreign-currency obligation"),
  REALIZED_FOREIGN_EXCHANGE_SETTLEMENT("realized foreign-exchange settlement"),
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
