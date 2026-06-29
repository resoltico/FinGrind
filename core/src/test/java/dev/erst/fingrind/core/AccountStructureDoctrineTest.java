package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.AccountDoctrineTestSupport.assetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.nominalTaxonomy;
import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct doctrinal coverage for account-node structure and hierarchy compatibility. */
class AccountStructureDoctrineTest {
  @Test
  void allowsPostingAndAllowsChildren_followDeclaredNodeKind() {
    AccountTaxonomy postableCurrentAsset =
        assetTaxonomy(
            AccountNodeKind.POSTABLE,
            FinancialPositionLineClassification.CURRENT_ASSET,
            CashFlowAssetClassification.NON_CASH);
    AccountTaxonomy headerCurrentAsset =
        assetTaxonomy(
            AccountNodeKind.HEADER,
            FinancialPositionLineClassification.CURRENT_ASSET,
            CashFlowAssetClassification.NON_CASH);

    assertTrue(AccountStructureDoctrine.allowsPosting(postableCurrentAsset));
    assertFalse(AccountStructureDoctrine.allowsChildren(postableCurrentAsset));
    assertFalse(AccountStructureDoctrine.allowsPosting(headerCurrentAsset));
    assertTrue(AccountStructureDoctrine.allowsChildren(headerCurrentAsset));

    assertThrows(
        NullPointerException.class, () -> AccountStructureDoctrine.allowsPosting(nullOf()));
    assertThrows(
        NullPointerException.class, () -> AccountStructureDoctrine.allowsChildren(nullOf()));
  }

  @Test
  void parentChildHierarchyCompatible_requiresSharedStatementMeaning() {
    AccountTaxonomy parentAssetHeader =
        assetTaxonomy(
            AccountNodeKind.HEADER,
            FinancialPositionLineClassification.CURRENT_ASSET,
            CashFlowAssetClassification.NON_CASH);
    AccountTaxonomy matchingAssetChild =
        assetTaxonomy(
            AccountNodeKind.POSTABLE,
            FinancialPositionLineClassification.CURRENT_ASSET,
            CashFlowAssetClassification.NON_CASH);
    AccountTaxonomy mismatchedAssetChild =
        assetTaxonomy(
            AccountNodeKind.POSTABLE,
            FinancialPositionLineClassification.NONCURRENT_ASSET,
            CashFlowAssetClassification.NON_CASH);
    AccountTaxonomy mismatchedAssetCashFlowChild =
        assetTaxonomy(
            AccountNodeKind.POSTABLE,
            FinancialPositionLineClassification.CURRENT_ASSET,
            CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT);
    AccountTaxonomy parentExpenseHeader =
        nominalTaxonomy(AccountNodeKind.HEADER, ProfitAndLossLineClassification.OPERATING_EXPENSE);
    AccountTaxonomy matchingExpenseChild =
        nominalTaxonomy(
            AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OPERATING_EXPENSE);
    AccountTaxonomy mismatchedExpenseChild =
        nominalTaxonomy(AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OTHER_EXPENSE);

    assertTrue(
        AccountStructureDoctrine.parentChildHierarchyCompatible(
            AccountType.ASSET, parentAssetHeader, matchingAssetChild));
    assertFalse(
        AccountStructureDoctrine.parentChildHierarchyCompatible(
            AccountType.ASSET, parentAssetHeader, mismatchedAssetChild));
    assertFalse(
        AccountStructureDoctrine.parentChildHierarchyCompatible(
            AccountType.ASSET, parentAssetHeader, mismatchedAssetCashFlowChild));
    assertTrue(
        AccountStructureDoctrine.parentChildHierarchyCompatible(
            AccountType.EXPENSE, parentExpenseHeader, matchingExpenseChild));
    assertFalse(
        AccountStructureDoctrine.parentChildHierarchyCompatible(
            AccountType.EXPENSE, parentExpenseHeader, mismatchedExpenseChild));

    assertThrows(
        NullPointerException.class,
        () ->
            AccountStructureDoctrine.parentChildHierarchyCompatible(
                nullOf(), parentAssetHeader, matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountStructureDoctrine.parentChildHierarchyCompatible(
                AccountType.ASSET, nullOf(), matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountStructureDoctrine.parentChildHierarchyCompatible(
                AccountType.ASSET, parentAssetHeader, nullOf()));
  }
}
