package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.AccountDoctrineTestSupport.balanceSheetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.nominalTaxonomy;
import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct doctrinal coverage for temporary-account closing and profit-and-loss contribution. */
class ProfitAndLossAccountDoctrineTest {
  @Test
  void closesTemporaryProfitAndLossAccountType_onlyIncludesNominalAccounts() {
    assertFalse(
        ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(AccountType.ASSET));
    assertFalse(
        ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(
            AccountType.LIABILITY));
    assertFalse(
        ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(AccountType.EQUITY));
    assertTrue(
        ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(AccountType.REVENUE));
    assertTrue(
        ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(AccountType.EXPENSE));
    assertThrows(
        NullPointerException.class,
        () -> ProfitAndLossAccountDoctrine.closesTemporaryProfitAndLossAccountType(nullOf()));
  }

  @Test
  void profitAndLossContributionMinorUnits_coversRevenueExpenseAndZeroBalances() {
    AccountTaxonomy revenueTaxonomy =
        nominalTaxonomy(
            AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.OPERATING_REVENUE);
    AccountTaxonomy expenseTaxonomy =
        nominalTaxonomy(AccountNodeKind.POSTABLE, ProfitAndLossLineClassification.COST_OF_SALES);

    assertEquals(
        100L,
        ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, revenueTaxonomy, BalanceSide.CREDIT, 100L));
    assertEquals(
        -100L,
        ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, revenueTaxonomy, BalanceSide.DEBIT, 100L));
    assertEquals(
        -40L,
        ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
            AccountType.EXPENSE, expenseTaxonomy, BalanceSide.DEBIT, 40L));
    assertEquals(
        40L,
        ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
            AccountType.EXPENSE, expenseTaxonomy, BalanceSide.CREDIT, 40L));
    assertEquals(
        0L,
        ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, revenueTaxonomy, BalanceSide.ZERO, 0L));

    IllegalArgumentException accountTypeFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
                    AccountType.ASSET,
                    balanceSheetTaxonomy(
                        AccountNodeKind.POSTABLE,
                        FinancialPositionLineClassification.CURRENT_ASSET),
                    BalanceSide.DEBIT,
                    1L));
    assertEquals(
        "Only REVENUE and EXPENSE accounts contribute to current-period profit or loss.",
        accountTypeFailure.getMessage());

    IllegalArgumentException amountFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
                    AccountType.REVENUE, revenueTaxonomy, BalanceSide.CREDIT, -1L));
    assertEquals("amountMinor must not be negative.", amountFailure.getMessage());

    IllegalArgumentException zeroBalanceFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
                    AccountType.REVENUE, revenueTaxonomy, BalanceSide.ZERO, 5L));
    assertEquals(
        "ZERO balanceSide requires amountMinor to be zero.", zeroBalanceFailure.getMessage());

    assertThrows(
        NullPointerException.class,
        () ->
            ProfitAndLossAccountDoctrine.profitAndLossContributionMinorUnits(
                AccountType.REVENUE, revenueTaxonomy, nullOf(), 1L));
  }
}
