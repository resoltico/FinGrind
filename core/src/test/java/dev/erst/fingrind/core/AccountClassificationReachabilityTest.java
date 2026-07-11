package dev.erst.fingrind.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests for the canonical current-kernel account-classification reachability owner. */
class AccountClassificationReachabilityTest {
  @Test
  void currentKernelPublishesEveryDeclaredClassificationWithStableFlags() {
    List<AccountClassificationReachability.ReachabilityCell> cells =
        AccountClassificationReachability.currentKernel();

    assertEquals(
        FinancialPositionLineClassification.values().length
            + ProfitAndLossLineClassification.values().length,
        cells.size());
    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "financial-position", AccountType.ASSET, "CURRENT_ASSET", true, true, true, true),
        cells.getFirst());
    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "profit-and-loss", AccountType.EXPENSE, "OTHER_EXPENSE", true, false, true, true),
        cells.getLast());
    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "financial-position", AccountType.ASSET, "INVENTORY", true, true, false, true),
        cells.stream()
            .filter(cell -> "INVENTORY".equals(cell.classification()))
            .findFirst()
            .orElseThrow());
    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "financial-position", AccountType.EQUITY, "RESULT_HOLDING", true, true, false, false),
        cells.stream()
            .filter(cell -> "RESULT_HOLDING".equals(cell.classification()))
            .findFirst()
            .orElseThrow());
    assertTrue(
        cells.stream().allMatch(AccountClassificationReachability.ReachabilityCell::declarable));
    assertEquals(
        FinancialPositionLineClassification.values().length,
        cells.stream()
            .filter(AccountClassificationReachability.ReachabilityCell::openingReachable)
            .count());
  }

  @Test
  void reachabilityForMapsValidatedAccountTaxonomiesBackToTheCurrentKernelMatrix() {
    AccountTaxonomy reserveTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.RESERVE),
            Optional.empty());
    AccountTaxonomy expenseTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.empty(),
            Optional.of(ProfitAndLossLineClassification.DEPRECIATION_AND_AMORTIZATION));
    AccountTaxonomy inventoryTaxonomy =
        new AccountTaxonomy(
            AccountNodeKind.POSTABLE,
            Optional.empty(),
            Optional.of(FinancialPositionLineClassification.INVENTORY),
            Optional.empty());

    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "financial-position", AccountType.EQUITY, "RESERVE", true, true, true, true),
        AccountClassificationReachability.reachabilityFor(reserveTaxonomy));
    assertEquals(
        new AccountClassificationReachability.ReachabilityCell(
            "profit-and-loss",
            AccountType.EXPENSE,
            "DEPRECIATION_AND_AMORTIZATION",
            true,
            false,
            true,
            true),
        AccountClassificationReachability.reachabilityFor(expenseTaxonomy));
    assertTrue(AccountClassificationReachability.openingReachable(reserveTaxonomy));
    assertTrue(AccountClassificationReachability.openingReachable(inventoryTaxonomy));
    assertFalse(AccountClassificationReachability.openingReachable(expenseTaxonomy));
    assertFalse(AccountClassificationReachability.operationalJournalReachable(inventoryTaxonomy));
    assertTrue(AccountClassificationReachability.operationalJournalReachable(expenseTaxonomy));
    assertTrue(AccountClassificationReachability.reversalReachable(reserveTaxonomy));
  }

  @Test
  void reachabilityRejectsAmbiguousOrInvalidClassificationShapes() {
    IllegalArgumentException ambiguousTaxonomy =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                AccountClassificationReachability.reachabilityFor(
                    new AccountTaxonomy(
                        AccountNodeKind.POSTABLE,
                        Optional.empty(),
                        Optional.of(FinancialPositionLineClassification.CURRENT_ASSET),
                        Optional.of(ProfitAndLossLineClassification.OPERATING_REVENUE))));
    assertEquals(
        "Account taxonomy must carry exactly one classification family.",
        ambiguousTaxonomy.getMessage());

    IllegalArgumentException blankClassificationFamily =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    " ", AccountType.ASSET, "CURRENT_ASSET", true, true, true, true));
    assertEquals("classificationFamily must not be blank.", blankClassificationFamily.getMessage());

    IllegalArgumentException blankClassification =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    "financial-position", AccountType.ASSET, " ", true, true, true, true));
    assertEquals("classification must not be blank.", blankClassification.getMessage());

    IllegalArgumentException nonDeclarableReachability =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    "financial-position",
                    AccountType.ASSET,
                    "CURRENT_ASSET",
                    false,
                    true,
                    false,
                    false));
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableReachability.getMessage());

    IllegalArgumentException nonDeclarableOperationalReachability =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    "financial-position",
                    AccountType.ASSET,
                    "CURRENT_ASSET",
                    false,
                    false,
                    true,
                    false));
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableOperationalReachability.getMessage());

    IllegalArgumentException nonDeclarableReversalReachability =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    "financial-position",
                    AccountType.ASSET,
                    "CURRENT_ASSET",
                    false,
                    false,
                    false,
                    true));
    assertEquals(
        "Non-declarable reachability cells must not report any reachable write path.",
        nonDeclarableReversalReachability.getMessage());

    AccountClassificationReachability.ReachabilityCell unreachableButValidCell =
        assertDoesNotThrow(
            () ->
                new AccountClassificationReachability.ReachabilityCell(
                    "financial-position",
                    AccountType.ASSET,
                    "CURRENT_ASSET",
                    false,
                    false,
                    false,
                    false));
    assertFalse(unreachableButValidCell.declarable());
    assertFalse(unreachableButValidCell.openingReachable());
    assertFalse(unreachableButValidCell.operationalJournalReachable());
    assertFalse(unreachableButValidCell.reversalReachable());
  }
}
