package dev.erst.fingrind.jazzer.tool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import org.junit.jupiter.api.Test;

/** Covers direct invariant helpers behind deterministic inventory-costing fuzz support. */
class JazzerInventoryCostingMathSupportTest {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");

  @Test
  void exactPoolMathInvariantHolds_accepts_only_exact_pool_derived_costs() {
    WeightedAverageCostingMath.Disposal exactDisposal =
        new WeightedAverageCostingMath.Disposal(
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.ofScaledUnits(0, 49L), Money.ofMinorUnits(EUR, 926L)),
            Money.ofMinorUnits(EUR, 567L));
    WeightedAverageCostingMath.Disposal projectionBasedSurrogateDisposal =
        new WeightedAverageCostingMath.Disposal(
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.ofScaledUnits(0, 49L), Money.ofMinorUnits(EUR, 923L)),
            Money.ofMinorUnits(EUR, 570L));

    assertTrue(
        JazzerInventoryCostingMathSupport.exactPoolMathInvariantHolds(
            Money.ofMinorUnits(EUR, 567L), exactDisposal));
    assertFalse(
        JazzerInventoryCostingMathSupport.exactPoolMathInvariantHolds(
            Money.ofMinorUnits(EUR, 567L), projectionBasedSurrogateDisposal));
  }

  @Test
  void zeroToZeroInvariantHolds_rejects_mismatched_zero_state() {
    assertTrue(
        JazzerInventoryCostingMathSupport.zeroToZeroInvariantHolds(
            Quantity.zero(0), Money.zero(EUR)));
    assertTrue(
        JazzerInventoryCostingMathSupport.zeroToZeroInvariantHolds(
            Quantity.ofScaledUnits(0, 49L), Money.ofMinorUnits(EUR, 926L)));
    assertFalse(
        JazzerInventoryCostingMathSupport.zeroToZeroInvariantHolds(
            Quantity.ofScaledUnits(0, 1L), Money.zero(EUR)));
  }

  @Test
  void knownProjectionMismatchCaseHolds_rejects_drifted_known_case() {
    assertTrue(
        JazzerInventoryCostingMathSupport.knownProjectionMismatchCaseHolds(
            Money.ofMinorUnits(EUR, 3L), Money.ofMinorUnits(EUR, 2L)));
    assertFalse(
        JazzerInventoryCostingMathSupport.knownProjectionMismatchCaseHolds(
            Money.ofMinorUnits(EUR, 2L), Money.ofMinorUnits(EUR, 2L)));
    assertFalse(
        JazzerInventoryCostingMathSupport.knownProjectionMismatchCaseHolds(
            Money.ofMinorUnits(EUR, 3L), Money.ofMinorUnits(EUR, 3L)));
  }

  @Test
  void projectionMismatchInvariantHolds_accepts_only_exact_non_projection_costs() {
    WeightedAverageCostingMath.Disposal exactDisposal =
        new WeightedAverageCostingMath.Disposal(
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 1L)),
            Money.ofMinorUnits(EUR, 3L));
    WeightedAverageCostingMath.Disposal projectionBasedDisposal =
        new WeightedAverageCostingMath.Disposal(
            new WeightedAverageCostingMath.InventoryPool(
                Quantity.ofScaledUnits(0, 1L), Money.ofMinorUnits(EUR, 2L)),
            Money.ofMinorUnits(EUR, 2L));

    assertTrue(
        JazzerInventoryCostingMathSupport.projectionMismatchInvariantHolds(
            Money.ofMinorUnits(EUR, 3L), Money.ofMinorUnits(EUR, 2L), exactDisposal));
    assertFalse(
        JazzerInventoryCostingMathSupport.projectionMismatchInvariantHolds(
            Money.ofMinorUnits(EUR, 3L), Money.ofMinorUnits(EUR, 3L), exactDisposal));
    assertFalse(
        JazzerInventoryCostingMathSupport.projectionMismatchInvariantHolds(
            Money.ofMinorUnits(EUR, 3L), Money.ofMinorUnits(EUR, 2L), projectionBasedDisposal));
  }
}
