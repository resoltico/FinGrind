package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Direct doctrinal coverage for account polarity and period-close contribution semantics. */
class AccountSemanticsTest {
  @Test
  void validate_andNormalBalance_coverOrdinaryContraAndRetainedEarningsRoles() {
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
        AccountSemantics.normalBalance(AccountType.EQUITY, AccountRole.RETAINED_EARNINGS));
    assertEquals(
        NormalBalance.DEBIT,
        AccountSemantics.normalBalance(AccountType.REVENUE, AccountRole.CONTRA));
    assertEquals(
        NormalBalance.CREDIT,
        AccountSemantics.normalBalance(AccountType.EXPENSE, AccountRole.CONTRA));

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> AccountSemantics.validate(AccountType.ASSET, AccountRole.RETAINED_EARNINGS));
    assertEquals("RETAINED_EARNINGS accounts must use accountType EQUITY.", exception.getMessage());
    assertThrows(
        NullPointerException.class,
        () -> AccountSemantics.validate(nullOf(), AccountRole.ORDINARY));
    assertThrows(
        NullPointerException.class, () -> AccountSemantics.validate(AccountType.ASSET, nullOf()));
  }

  @Test
  void closesIntoRetainedEarnings_onlyIncludesNominalAccounts() {
    assertFalse(AccountSemantics.closesIntoRetainedEarnings(AccountType.ASSET));
    assertFalse(AccountSemantics.closesIntoRetainedEarnings(AccountType.LIABILITY));
    assertFalse(AccountSemantics.closesIntoRetainedEarnings(AccountType.EQUITY));
    assertTrue(AccountSemantics.closesIntoRetainedEarnings(AccountType.REVENUE));
    assertTrue(AccountSemantics.closesIntoRetainedEarnings(AccountType.EXPENSE));
    assertThrows(
        NullPointerException.class, () -> AccountSemantics.closesIntoRetainedEarnings(nullOf()));
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
        -5L,
        AccountSemantics.profitAndLossContributionMinorUnits(
            AccountType.REVENUE, AccountRole.ORDINARY, BalanceSide.ZERO, 5L));

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

    assertThrows(
        NullPointerException.class,
        () ->
            AccountSemantics.profitAndLossContributionMinorUnits(
                AccountType.REVENUE, AccountRole.ORDINARY, nullOf(), 1L));
  }
}
