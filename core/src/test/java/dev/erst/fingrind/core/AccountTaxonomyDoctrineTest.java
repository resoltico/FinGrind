package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.AccountDoctrineTestSupport.assetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.balanceSheetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.nominalTaxonomy;
import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct doctrinal coverage for account taxonomy validation, polarity, and cash classification. */
class AccountTaxonomyDoctrineTest {
  @Test
  void validate_andNormalBalance_coverEveryClassificationAndRequiredTaxonomy() {
    assertEquals(
        NormalBalance.DEBIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.ASSET,
            assetTaxonomy(
                AccountNodeKind.POSTABLE,
                FinancialPositionLineClassification.CURRENT_ASSET,
                CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertEquals(
        NormalBalance.CREDIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.LIABILITY,
            balanceSheetTaxonomy(
                AccountNodeKind.POSTABLE, FinancialPositionLineClassification.CURRENT_LIABILITY)));
    assertEquals(
        NormalBalance.DEBIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.EQUITY,
            balanceSheetTaxonomy(
                AccountNodeKind.POSTABLE, FinancialPositionLineClassification.EQUITY_WITHDRAWAL)));
    assertEquals(
        NormalBalance.CREDIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.EQUITY,
            balanceSheetTaxonomy(
                AccountNodeKind.POSTABLE, FinancialPositionLineClassification.RESULT_HOLDING)));
    assertEquals(
        NormalBalance.CREDIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.REVENUE,
            nominalTaxonomy(
                AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OPERATING_REVENUE)));
    assertEquals(
        NormalBalance.DEBIT,
        AccountTaxonomyDoctrine.normalBalance(
            AccountType.EXPENSE,
            nominalTaxonomy(
                AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.COST_OF_SALES)));

    IllegalArgumentException balanceSheetMissing =
        assertThrows(
            IllegalArgumentException.class,
            () -> AccountTaxonomyDoctrine.validate(AccountType.ASSET, AccountTaxonomy.empty()));
    assertEquals(
        "Financial-position classification is required for balance-sheet accounts.",
        balanceSheetMissing.getMessage());

    IllegalArgumentException assetCashFlowMissing =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.ASSET,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.empty(),
                        Optional.empty())));
    assertEquals(
        "cashFlowAssetClassification is required for ASSET accounts. Accepted values: CASH_AND_CASH_EQUIVALENT, NON_CASH.",
        assetCashFlowMissing.getMessage());

    IllegalArgumentException balanceSheetProfitAndLossFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.ASSET,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                        Optional.of(CashFlowAssetClassification.NON_CASH))));
    assertEquals(
        "Profit-and-loss classification must be absent for balance-sheet accounts.",
        balanceSheetProfitAndLossFailure.getMessage());

    IllegalArgumentException nonAssetCashFlowFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.LIABILITY,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
                        Optional.empty(),
                        Optional.of(CashFlowAssetClassification.NON_CASH))));
    assertEquals(
        "cashFlowAssetClassification must be absent for non-ASSET accounts.",
        nonAssetCashFlowFailure.getMessage());

    IllegalArgumentException balanceSheetMismatchFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.ASSET,
                    assetTaxonomy(
                        AccountNodeKind.POSTABLE,
                        FinancialPositionLineClassification.CURRENT_LIABILITY,
                        CashFlowAssetClassification.NON_CASH)));
    assertEquals(
        "Financial-position classification must match the declared accountType.",
        balanceSheetMismatchFailure.getMessage());

    IllegalArgumentException nominalMissing =
        assertThrows(
            IllegalArgumentException.class,
            () -> AccountTaxonomyDoctrine.validate(AccountType.REVENUE, AccountTaxonomy.empty()));
    assertEquals(
        "Profit-and-loss classification is required for nominal accounts.",
        nominalMissing.getMessage());

    IllegalArgumentException nominalMismatch =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.REVENUE,
                    nominalTaxonomy(
                        AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.COST_OF_SALES)));
    assertEquals(
        "Profit-and-loss classification must match the declared accountType.",
        nominalMismatch.getMessage());

    IllegalArgumentException nominalFinancialPositionFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.REVENUE,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                        Optional.empty())));
    assertEquals(
        "Financial-position classification must be absent for nominal accounts.",
        nominalFinancialPositionFailure.getMessage());

    IllegalArgumentException nominalCashFlowFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.EXPENSE,
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(ProfitAndLossLineClassification.COST_OF_SALES),
                        Optional.of(CashFlowAssetClassification.NON_CASH))));
    assertEquals(
        "cashFlowAssetClassification must be absent for nominal accounts.",
        nominalCashFlowFailure.getMessage());

    assertDoesNotThrow(
        () ->
            AccountTaxonomyDoctrine.validate(
                AccountType.EQUITY,
                balanceSheetTaxonomy(
                    AccountNodeKind.POSTABLE, FinancialPositionLineClassification.RESULT_HOLDING)));
    assertDoesNotThrow(
        () ->
            AccountTaxonomyDoctrine.validate(
                AccountType.LIABILITY,
                balanceSheetTaxonomy(
                    AccountNodeKind.POSTABLE,
                    FinancialPositionLineClassification.CURRENT_LIABILITY)));
    assertDoesNotThrow(
        () ->
            AccountTaxonomyDoctrine.validate(
                AccountType.EXPENSE,
                nominalTaxonomy(
                    AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.COST_OF_SALES)));

    assertThrows(
        NullPointerException.class,
        () -> AccountTaxonomyDoctrine.validate(nullOf(), AccountTaxonomy.empty()));
    assertThrows(
        NullPointerException.class,
        () -> AccountTaxonomyDoctrine.validate(AccountType.ASSET, nullOf()));
  }

  @Test
  void cashAndCashEquivalent_requiresValidatedAssetTaxonomy() {
    assertTrue(
        AccountTaxonomyDoctrine.cashAndCashEquivalent(
            AccountType.ASSET,
            assetTaxonomy(
                AccountNodeKind.POSTABLE,
                FinancialPositionLineClassification.CURRENT_ASSET,
                CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
    assertFalse(
        AccountTaxonomyDoctrine.cashAndCashEquivalent(
            AccountType.ASSET,
            assetTaxonomy(
                AccountNodeKind.POSTABLE,
                FinancialPositionLineClassification.CURRENT_ASSET,
                CashFlowAssetClassification.NON_CASH)));
    assertFalse(
        AccountTaxonomyDoctrine.cashAndCashEquivalent(
            AccountType.LIABILITY,
            balanceSheetTaxonomy(
                AccountNodeKind.POSTABLE, FinancialPositionLineClassification.CURRENT_LIABILITY)));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            AccountTaxonomyDoctrine.cashAndCashEquivalent(
                AccountType.ASSET,
                new AccountTaxonomy(
                    AccountNodeKind.POSTABLE,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                    Optional.empty(),
                    Optional.empty())));
    assertThrows(
        IllegalArgumentException.class,
        () -> AccountTaxonomyDoctrine.normalBalance(AccountType.ASSET, AccountTaxonomy.empty()));
  }

  @Test
  void contraAccounts_mustBePostableAndInvertTheirNormalBalance() {
    AccountTaxonomy revenueContra =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(new AccountCode("sales-discount-allowance")),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE),
            Optional.empty());

    assertEquals(
        NormalBalance.DEBIT,
        AccountTaxonomyDoctrine.normalBalance(AccountType.REVENUE, revenueContra));
    AccountTaxonomy expenseContra =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(new AccountCode("operating-expense")),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.OPERATING_EXPENSE),
            Optional.empty());
    assertEquals(
        NormalBalance.CREDIT,
        AccountTaxonomyDoctrine.normalBalance(AccountType.EXPENSE, expenseContra));

    IllegalArgumentException headerContra =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountTaxonomyDoctrine.validate(
                    AccountType.REVENUE,
                    new AccountTaxonomy(
                        AccountNodeKind.HEADER,
                        Optional.empty(),
                        Optional.of(new AccountCode("operating-revenue")),
                        Optional.empty(),
                        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE),
                        Optional.empty())));
    assertEquals("Contra accounts must be declared as POSTABLE.", headerContra.getMessage());
  }
}
