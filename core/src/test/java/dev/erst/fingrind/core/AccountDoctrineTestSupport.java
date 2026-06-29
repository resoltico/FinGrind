package dev.erst.fingrind.core;

import java.util.Optional;

/** Shared test-only taxonomy builders for doctrine-focused account coverage. */
final class AccountDoctrineTestSupport {
  private AccountDoctrineTestSupport() {}

  static AccountTaxonomy balanceSheetTaxonomy(
      AccountNodeKind nodeKind,
      FinancialPositionLineClassification financialPositionLineClassification) {
    return new AccountTaxonomy(
        nodeKind,
        Optional.empty(),
        Optional.of(financialPositionLineClassification),
        Optional.empty(),
        financialPositionLineClassification.accountType() == AccountType.ASSET
            ? Optional.of(CashFlowAssetClassification.NON_CASH)
            : Optional.empty());
  }

  static AccountTaxonomy assetTaxonomy(
      AccountNodeKind nodeKind,
      FinancialPositionLineClassification financialPositionLineClassification,
      CashFlowAssetClassification cashFlowAssetClassification) {
    return new AccountTaxonomy(
        nodeKind,
        Optional.empty(),
        Optional.of(financialPositionLineClassification),
        Optional.empty(),
        Optional.of(cashFlowAssetClassification));
  }

  static AccountTaxonomy nominalTaxonomy(
      AccountNodeKind nodeKind, ProfitAndLossLineClassification profitAndLossLineClassification) {
    return new AccountTaxonomy(
        nodeKind, Optional.empty(), Optional.empty(), Optional.of(profitAndLossLineClassification));
  }
}
