package dev.erst.fingrind.core;

import java.util.List;
import java.util.Objects;

/** Role vocabulary derived from declared account taxonomy for journal classification. */
public enum AccountRole implements WireValue {
  CASH,
  INVENTORY,
  PREPAID_EXPENSE,
  RECEIVABLE,
  PAYABLE,
  DEFERRED_REVENUE,
  ACCRUED_EXPENSE,
  REVENUE,
  EXPENSE,
  EQUITY_CONTRIBUTED,
  EQUITY_DRAWS,
  SETTLEMENT_ADJUNCT,
  AUX;

  /** Returns whether this role participates in typed event anchoring. */
  public boolean anchorRole() {
    return this != SETTLEMENT_ADJUNCT && this != AUX;
  }

  @Override
  public String wireValue() {
    return name();
  }

  /** Returns every stable wire value in declaration order. */
  public static List<String> wireValues() {
    return WireValue.wireValues(AccountRole.class);
  }

  /** Parses one stable wire value. */
  public static AccountRole fromWireValue(String wireValue) {
    return WireValue.fromWireValue(AccountRole.class, wireValue, "Unsupported accountRole");
  }

  /** Derives the classifier role from the declared account type and taxonomy. */
  public static AccountRole from(AccountType accountType, AccountTaxonomy accountTaxonomy) {
    Objects.requireNonNull(accountType, "accountType");
    Objects.requireNonNull(accountTaxonomy, "accountTaxonomy");
    AccountTaxonomyDoctrine.validate(accountType, accountTaxonomy);
    if (cashEquivalentAsset(accountType, accountTaxonomy)) {
      return CASH;
    }
    if (accountTaxonomy.financialPositionLineClassification().isPresent()) {
      return financialPositionRole(
          accountTaxonomy.financialPositionLineClassification().orElseThrow());
    }
    return profitAndLossRole(accountTaxonomy.profitAndLossLineClassification().orElseThrow());
  }

  private static boolean cashEquivalentAsset(
      AccountType accountType, AccountTaxonomy accountTaxonomy) {
    return accountType == AccountType.ASSET
        && accountTaxonomy.cashFlowAssetClassification().orElseThrow().cashAndCashEquivalent();
  }

  private static AccountRole financialPositionRole(
      FinancialPositionLineClassification classification) {
    return Objects.requireNonNull(classification, "classification").classifierRole();
  }

  private static AccountRole profitAndLossRole(ProfitAndLossLineClassification classification) {
    return switch (classification) {
      case SALES_DISCOUNT_ALLOWANCE, SETTLEMENT_FEE, BAD_DEBT_WRITE_OFF -> SETTLEMENT_ADJUNCT;
      case FINANCE_INCOME, FINANCE_EXPENSE -> AUX;
      case OPERATING_REVENUE, OTHER_REVENUE -> REVENUE;
      case COST_OF_SALES, OPERATING_EXPENSE, DEPRECIATION_AND_AMORTIZATION, OTHER_EXPENSE ->
          EXPENSE;
    };
  }
}
