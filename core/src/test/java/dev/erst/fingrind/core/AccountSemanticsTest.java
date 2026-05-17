package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct doctrinal coverage for account polarity and period-close contribution semantics. */
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
                        java.util.Optional.empty(),
                        java.util.Optional.of(
                            FinancialPositionLineClassification.CURRENT_LIABILITY),
                        java.util.Optional.empty())));
    assertEquals(
        "Financial-position classification must match the declared accountType.",
        balanceSheetMismatchFailure.getMessage());
    IllegalArgumentException currentPeriodResultDeclaredAccountFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountSemantics.validate(
                    AccountType.EQUITY,
                    AccountRole.ORDINARY,
                    new AccountTaxonomy(
                        java.util.Optional.empty(),
                        java.util.Optional.of(
                            FinancialPositionLineClassification.CURRENT_PERIOD_RESULT),
                        java.util.Optional.empty())));
    assertEquals(
        "CURRENT_PERIOD_RESULT is reserved for derived statement rows and must not be declared on accounts.",
        currentPeriodResultDeclaredAccountFailure.getMessage());
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
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.RETAINED_EARNINGS),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountSemantics.validate(
                AccountType.LIABILITY,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
                    java.util.Optional.empty(),
                    java.util.Optional.of(FinancialPositionLineClassification.CURRENT_LIABILITY),
                    java.util.Optional.empty())));
    assertDoesNotThrow(
        () ->
            AccountSemantics.validate(
                AccountType.EXPENSE,
                AccountRole.ORDINARY,
                new AccountTaxonomy(
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
}
