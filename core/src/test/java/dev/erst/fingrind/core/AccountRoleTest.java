package dev.erst.fingrind.core;

import static dev.erst.fingrind.core.AccountDoctrineTestSupport.assetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.balanceSheetTaxonomy;
import static dev.erst.fingrind.core.AccountDoctrineTestSupport.nominalTaxonomy;
import static dev.erst.fingrind.core.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Covers stable account-role wire values and taxonomy-derived classifier role assignment. */
class AccountRoleTest {
  @Test
  void anchorRole_marksOnlyTypedEventRolesAsAnchors() {
    Set<AccountRole> anchors =
        EnumSet.of(
            AccountRole.CASH,
            AccountRole.INVENTORY,
            AccountRole.RECEIVABLE,
            AccountRole.PAYABLE,
            AccountRole.REVENUE,
            AccountRole.EXPENSE,
            AccountRole.EQUITY_CONTRIBUTED,
            AccountRole.EQUITY_DRAWS);
    for (AccountRole accountRole : AccountRole.values()) {
      if (anchors.contains(accountRole)) {
        assertTrue(accountRole.anchorRole(), accountRole.name());
      } else {
        assertFalse(accountRole.anchorRole(), accountRole.name());
      }
    }
  }

  @Test
  void wireValues_roundTripInDeclarationOrder() {
    assertEquals(
        List.of(
            "CASH",
            "INVENTORY",
            "RECEIVABLE",
            "PAYABLE",
            "REVENUE",
            "EXPENSE",
            "EQUITY_CONTRIBUTED",
            "EQUITY_DRAWS",
            "SETTLEMENT_ADJUNCT",
            "AUX"),
        AccountRole.wireValues());
    for (AccountRole accountRole : AccountRole.values()) {
      assertEquals(
          accountRole, AccountRole.fromWireValue(accountRole.wireValue()), accountRole.name());
    }

    IllegalArgumentException unsupported =
        assertThrows(
            IllegalArgumentException.class, () -> AccountRole.fromWireValue("UNSUPPORTED_ROLE"));
    assertEquals("Unsupported accountRole: UNSUPPORTED_ROLE", unsupported.getMessage());
  }

  @Test
  void from_recognizesCashEquivalentAssetsAheadOfFinancialPositionFallback() {
    assertEquals(
        AccountRole.CASH,
        AccountRole.from(
            AccountType.ASSET,
            assetTaxonomy(
                AccountNodeKind.POSTABLE,
                FinancialPositionLineClassification.CURRENT_ASSET,
                CashFlowAssetClassification.CASH_AND_CASH_EQUIVALENT)));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("financialPositionRows")
  void from_mapsFinancialPositionTaxonomy(
      FinancialPositionLineClassification classification, AccountRole expectedRole) {
    AccountTaxonomy taxonomy =
        classification.accountType() == AccountType.ASSET
            ? assetTaxonomy(
                AccountNodeKind.POSTABLE, classification, CashFlowAssetClassification.NON_CASH)
            : balanceSheetTaxonomy(AccountNodeKind.POSTABLE, classification);

    assertEquals(expectedRole, AccountRole.from(classification.accountType(), taxonomy));
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("profitAndLossRows")
  void from_mapsProfitAndLossTaxonomy(
      ProfitAndLossLineClassification classification, AccountRole expectedRole) {
    assertEquals(
        expectedRole,
        AccountRole.from(
            classification.accountType(),
            nominalTaxonomy(AccountNodeKind.POSTABLE, classification)));
  }

  @Test
  void from_requiresNonNullInputs() {
    assertThrows(
        NullPointerException.class, () -> AccountRole.from(nullOf(), AccountTaxonomy.empty()));
    assertThrows(NullPointerException.class, () -> AccountRole.from(AccountType.ASSET, nullOf()));
  }

  private static Stream<Arguments> financialPositionRows() {
    return Stream.of(
        Arguments.of(FinancialPositionLineClassification.CURRENT_ASSET, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.INVENTORY, AccountRole.INVENTORY),
        Arguments.of(FinancialPositionLineClassification.NONCURRENT_ASSET, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.TRADE_RECEIVABLE, AccountRole.RECEIVABLE),
        Arguments.of(FinancialPositionLineClassification.CURRENT_LIABILITY, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.NONCURRENT_LIABILITY, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.TRADE_PAYABLE, AccountRole.PAYABLE),
        Arguments.of(
            FinancialPositionLineClassification.EQUITY_CONTRIBUTION,
            AccountRole.EQUITY_CONTRIBUTED),
        Arguments.of(
            FinancialPositionLineClassification.EQUITY_WITHDRAWAL, AccountRole.EQUITY_DRAWS),
        Arguments.of(FinancialPositionLineClassification.RESULT_HOLDING, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.RETAINED_ACCUMULATED, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.RESERVE, AccountRole.AUX),
        Arguments.of(FinancialPositionLineClassification.OTHER_EQUITY, AccountRole.AUX));
  }

  private static Stream<Arguments> profitAndLossRows() {
    return Stream.of(
        Arguments.of(ProfitAndLossLineClassification.OPERATING_REVENUE, AccountRole.REVENUE),
        Arguments.of(
            ProfitAndLossLineClassification.SALES_DISCOUNT_ALLOWANCE,
            AccountRole.SETTLEMENT_ADJUNCT),
        Arguments.of(ProfitAndLossLineClassification.OTHER_REVENUE, AccountRole.REVENUE),
        Arguments.of(ProfitAndLossLineClassification.FINANCE_INCOME, AccountRole.AUX),
        Arguments.of(ProfitAndLossLineClassification.COST_OF_SALES, AccountRole.EXPENSE),
        Arguments.of(ProfitAndLossLineClassification.OPERATING_EXPENSE, AccountRole.EXPENSE),
        Arguments.of(
            ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION, AccountRole.EXPENSE),
        Arguments.of(
            ProfitAndLossLineClassification.SETTLEMENT_FEE, AccountRole.SETTLEMENT_ADJUNCT),
        Arguments.of(
            ProfitAndLossLineClassification.BAD_DEBT_WRITE_OFF, AccountRole.SETTLEMENT_ADJUNCT),
        Arguments.of(ProfitAndLossLineClassification.FINANCE_EXPENSE, AccountRole.AUX),
        Arguments.of(ProfitAndLossLineClassification.OTHER_EXPENSE, AccountRole.EXPENSE));
  }
}
