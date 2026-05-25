package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Direct doctrinal coverage for account polarity and period-result-transfer contribution semantics.
 */
class AccountSemanticsTest {
  @Test
  void validate_andNormalBalance_coverOrdinaryContraAndRequiredTaxonomy() {
    assertEquals(
        NormalBalance.DEBIT,
        AccountSemantics.normalBalance(AccountType.ASSET, AccountRole.ORDINARY));
    assertEquals(
        NormalBalance.CREDIT,
        AccountSemantics.normalBalance(AccountType.ASSET, AccountRole.CONTRA));
    assertEquals(
        NormalBalance.CREDIT,
        AccountSemantics.normalBalance(AccountType.LIABILITY, AccountRole.ORDINARY));
    assertEquals(
        NormalBalance.CREDIT,
        AccountSemantics.normalBalance(AccountType.EQUITY, AccountRole.ORDINARY));
    assertEquals(
        NormalBalance.DEBIT,
        AccountSemantics.normalBalance(AccountType.REVENUE, AccountRole.CONTRA));
    assertEquals(
        NormalBalance.CREDIT,
        AccountSemantics.normalBalance(AccountType.EXPENSE, AccountRole.CONTRA));
    assertEquals(
        NormalBalance.DEBIT,
        AccountSemantics.normalBalance(AccountType.EXPENSE, AccountRole.ORDINARY));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.ASSET, AccountRole.ORDINARY, AccountTaxonomy.empty()));
    assertEquals(
        "Financial-position classification is required for balance-sheet accounts.",
        exception.getMessage());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountSemantics.validate(
                AccountType.REVENUE,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    java.util.Optional.empty())));
    IllegalArgumentException balanceSheetProfitAndLossFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.ASSET,
                    AccountRole.ORDINARY,
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        java.util.Optional.empty(),
                        java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        java.util.Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE))));
    assertEquals(
        "Profit-and-loss classification must be absent for balance-sheet accounts.",
        balanceSheetProfitAndLossFailure.getMessage());
    IllegalArgumentException balanceSheetMismatchFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.ASSET,
                    AccountRole.ORDINARY,
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        java.util.Optional.empty(),
                        java.util.Optional.of(
                            FinancialPositionLineClassification.CURRENT_LIABILITY),
                        java.util.Optional.empty())));
    assertEquals(
        "Financial-position classification must match the declared accountType.",
        balanceSheetMismatchFailure.getMessage());
    IllegalArgumentException nominalClassificationMissingFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.REVENUE, AccountRole.ORDINARY, AccountTaxonomy.empty()));
    assertEquals(
        "Profit-and-loss classification is required for nominal accounts.",
        nominalClassificationMissingFailure.getMessage());
    IllegalArgumentException nominalClassificationMismatchFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.REVENUE,
                    AccountRole.ORDINARY,
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        java.util.Optional.empty(),
                        java.util.Optional.empty(),
                        java.util.Optional.of(ProfitAndLossLineClassification.COST_OF_SALES))));
    assertEquals(
        "Profit-and-loss classification must match the declared accountType.",
        nominalClassificationMismatchFailure.getMessage());
    IllegalArgumentException nominalFinancialPositionFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.REVENUE,
                    AccountRole.ORDINARY,
                    new AccountTaxonomy(
                        dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                        java.util.Optional.empty(),
                        java.util.Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        java.util.Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE))));
    assertEquals(
        "Financial-position classification must be absent for nominal accounts.",
        nominalFinancialPositionFailure.getMessage());
    assertDoesNotThrow(() -> AccountSemantics.validate(AccountType.REVENUE, AccountRole.ORDINARY));
    assertDoesNotThrow(
        () ->
            AccountSemantics.validate(
                AccountType.EQUITY,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.RESULT_HOLDING),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountSemantics.validate(
                AccountType.LIABILITY,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountSemantics.validate(
                AccountType.EXPENSE,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    dev.erst.fingrind.core.AccountNodeKind.POSTABLE,
                    java.util.Optional.empty(),
                    java.util.Optional.empty(),
                    java.util.Optional.of(ProfitAndLossLineClassification.COST_OF_SALES))));
    assertThrows(
        NullPointerException.class,
        () -> AccountSemantics.validate(nullOf(), AccountRole.ORDINARY));
    assertThrows(
        NullPointerException.class, () -> AccountSemantics.validate(AccountType.ASSET, nullOf()));
  }

  @Test
  void closesTemporaryProfitAndLossAccountType_onlyIncludesNominalAccounts() {
    assertFalse(AccountSemantics.closesTemporaryProfitAndLossAccountType(AccountType.ASSET));
    assertFalse(AccountSemantics.closesTemporaryProfitAndLossAccountType(AccountType.LIABILITY));
    assertFalse(AccountSemantics.closesTemporaryProfitAndLossAccountType(AccountType.EQUITY));
    assertTrue(AccountSemantics.closesTemporaryProfitAndLossAccountType(AccountType.REVENUE));
    assertTrue(AccountSemantics.closesTemporaryProfitAndLossAccountType(AccountType.EXPENSE));
    assertThrows(
        NullPointerException.class,
        () -> AccountSemantics.closesTemporaryProfitAndLossAccountType(nullOf()));
  }

  @Test
  void profitAndLossContributionMinorUnits_coversRevenueExpenseContraAndZeroBalances() {
    assertEquals(
        100L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.CREDIT, 100L));
    assertEquals(
        -100L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.DEBIT, 100L));
    assertEquals(
        -100L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, AccountRole.CONTRA, BalanceSide.DEBIT, 100L));
    assertEquals(
        -40L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.EXPENSE, AccountRole.ORDINARY, BalanceSide.DEBIT, 40L));
    assertEquals(
        40L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.EXPENSE, AccountRole.ORDINARY, BalanceSide.CREDIT, 40L));
    assertEquals(
        25L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.EXPENSE, AccountRole.CONTRA, BalanceSide.CREDIT, 25L));
    assertEquals(
        0L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.ZERO, 0L));

    IllegalArgumentException accountTypeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.profitAndLossContributionMinorUnits(
                    AccountType.ASSET, AccountRole.ORDINARY, BalanceSide.DEBIT, 1L));
    assertEquals(
        "Only REVENUE and EXPENSE accounts contribute to current-period profit or loss.",
        accountTypeFailure.getMessage());

    IllegalArgumentException amountFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.profitAndLossContributionMinorUnits(
                    AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.CREDIT, -1L));
    assertEquals("amountMinor must not be negative.", amountFailure.getMessage());

    IllegalArgumentException zeroBalanceFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.profitAndLossContributionMinorUnits(
                    AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.ZERO, 5L));
    assertEquals(
        "ZERO balanceSide requires amountMinor to be zero.", zeroBalanceFailure.getMessage());

    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.profitAndLossContributionMinorUnits(
                AccountType.REVENUE, AccountRole.ORDINARY, nullOf(), 1L));
  }

  @Test
  void allowsPostingAndAllowsChildren_followDeclaredNodeKind() {
    AccountTaxonomy postableCurrentAsset =
        balanceSheetTaxonomy(
            AccountNodeKind.POSTABLE, FinancialPositionLineClassification.CURRENT_ASSET);
    AccountTaxonomy headerCurrentAsset =
        balanceSheetTaxonomy(
            AccountNodeKind.HEADER, FinancialPositionLineClassification.CURRENT_ASSET);

    assertTrue(AccountSemantics.allowsPosting(postableCurrentAsset));
    assertFalse(AccountSemantics.allowsChildren(postableCurrentAsset));
    assertFalse(AccountSemantics.allowsPosting(headerCurrentAsset));
    assertTrue(AccountSemantics.allowsChildren(headerCurrentAsset));

    assertThrows(NullPointerException.class, () -> AccountSemantics.allowsPosting(nullOf()));
    assertThrows(NullPointerException.class, () -> AccountSemantics.allowsChildren(nullOf()));
  }

  @Test
  void parentChildHierarchyCompatible_requiresSharedRoleAndSharedStatementMeaning() {
    AccountTaxonomy parentAssetHeader =
        balanceSheetTaxonomy(
            AccountNodeKind.HEADER, FinancialPositionLineClassification.CURRENT_ASSET);
    AccountTaxonomy matchingAssetChild =
        balanceSheetTaxonomy(
            AccountNodeKind.POSTABLE, FinancialPositionLineClassification.CURRENT_ASSET);
    AccountTaxonomy mismatchedAssetChild =
        balanceSheetTaxonomy(
            AccountNodeKind.POSTABLE, FinancialPositionLineClassification.NONCURRENT_ASSET);
    AccountTaxonomy parentExpenseHeader =
        nominalTaxonomy(AccountNodeKind.HEADER, ProfitAndLossLineClassification.OPERATING_EXPENSE);
    AccountTaxonomy matchingExpenseChild =
        nominalTaxonomy(
            AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OPERATING_EXPENSE);
    AccountTaxonomy mismatchedExpenseChild =
        nominalTaxonomy(AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OTHER_EXPENSE);

    assertTrue(
        AccountSemantics.parentChildHierarchyCompatible(
            AccountType.ASSET,
            AccountRole.ORDINARY,
            parentAssetHeader,
            AccountRole.ORDINARY,
            matchingAssetChild));
    assertFalse(
        AccountSemantics.parentChildHierarchyCompatible(
            AccountType.ASSET,
            AccountRole.ORDINARY,
            parentAssetHeader,
            AccountRole.ORDINARY,
            mismatchedAssetChild));
    assertFalse(
        AccountSemantics.parentChildHierarchyCompatible(
            AccountType.ASSET,
            AccountRole.ORDINARY,
            parentAssetHeader,
            AccountRole.CONTRA,
            matchingAssetChild));

    assertTrue(
        AccountSemantics.parentChildHierarchyCompatible(
            AccountType.EXPENSE,
            AccountRole.ORDINARY,
            parentExpenseHeader,
            AccountRole.ORDINARY,
            matchingExpenseChild));
    assertFalse(
        AccountSemantics.parentChildHierarchyCompatible(
            AccountType.EXPENSE,
            AccountRole.ORDINARY,
            parentExpenseHeader,
            AccountRole.ORDINARY,
            mismatchedExpenseChild));

    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.parentChildHierarchyCompatible(
                nullOf(),
                AccountRole.ORDINARY,
                parentAssetHeader,
                AccountRole.ORDINARY,
                matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.parentChildHierarchyCompatible(
                AccountType.ASSET,
                nullOf(),
                parentAssetHeader,
                AccountRole.ORDINARY,
                matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.parentChildHierarchyCompatible(
                AccountType.ASSET,
                AccountRole.ORDINARY,
                nullOf(),
                AccountRole.ORDINARY,
                matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.parentChildHierarchyCompatible(
                AccountType.ASSET,
                AccountRole.ORDINARY,
                parentAssetHeader,
                nullOf(),
                matchingAssetChild));
    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.parentChildHierarchyCompatible(
                AccountType.ASSET,
                AccountRole.ORDINARY,
                parentAssetHeader,
                AccountRole.ORDINARY,
                nullOf()));
  }

  private static AccountTaxonomy balanceSheetTaxonomy(
      AccountNodeKind nodeKind,
      FinancialPositionLineClassification financialPositionLineClassification) {
    return new AccountTaxonomy(
        nodeKind,
        java.util.Optional.empty(),
        java.util.Optional.of(financialPositionLineClassification),
        java.util.Optional.empty());
  }

  private static AccountTaxonomy nominalTaxonomy(
      AccountNodeKind nodeKind, ProfitAndLossLineClassification profitAndLossLineClassification) {
    return new AccountTaxonomy(
        nodeKind,
        java.util.Optional.empty(),
        java.util.Optional.empty(),
        java.util.Optional.of(profitAndLossLineClassification));
  }
}
