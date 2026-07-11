package dev.erst.fingrind.jazzer.tool;

import dev.erst.fingrind.core.CurrencyUnit;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.util.Objects;
import java.util.SplittableRandom;

/** Shared assertions and replay details for inventory-costing fuzzing. */
public final class JazzerInventoryCostingMathSupport {
  private static final CurrencyUnit EUR = CurrencyUnit.of("EUR");
  private static final int SCENARIO_COUNT = 32;

  private JazzerInventoryCostingMathSupport() {}

  /** Asserts the weighted-average disposal invariants for one raw byte-seed input. */
  public static void assertExactPoolMath(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    long seed = InventoryCostingScenarioSupport.seedFrom(input);
    InventoryCostingScenarioSupport.ScenarioSample seedDerivedProjectionMismatch =
        InventoryCostingScenarioSupport.seedDerivedProjectionMismatchScenario(seed);
    JazzerInvariantAssertionSupport.require(
        projectionMismatchInvariantHolds(
            seedDerivedProjectionMismatch.exactCostOfSales(),
            seedDerivedProjectionMismatch.projectionBasedCostOfSales(),
            seedDerivedProjectionMismatch.disposal()),
        "Seed-derived weighted-average mismatch family must keep exact pool math distinct from one rounded projection-based surrogate.");
    JazzerInvariantAssertionSupport.require(
        zeroToZeroInvariantHolds(
            seedDerivedProjectionMismatch.disposal().remainingPool().quantityOnHand(),
            seedDerivedProjectionMismatch.disposal().remainingPool().costPool()),
        "Seed-derived weighted-average mismatch family must preserve zero-to-zero quantity and cost-pool truth.");
    assertKnownProjectionMismatchCase();

    SplittableRandom random = new SplittableRandom(seed);
    for (int scenario = 1; scenario < SCENARIO_COUNT; scenario++) {
      InventoryCostingScenarioSupport.ScenarioSample sample =
          InventoryCostingScenarioSupport.randomScenario(random);
      JazzerInvariantAssertionSupport.require(
          exactPoolMathInvariantHolds(sample.exactCostOfSales(), sample.disposal()),
          "Weighted-average disposal must keep reporting the direct exact pool-based cost of sales.");
      JazzerInvariantAssertionSupport.require(
          zeroToZeroInvariantHolds(
              sample.disposal().remainingPool().quantityOnHand(),
              sample.disposal().remainingPool().costPool()),
          "Remaining inventory pool must preserve zero-to-zero quantity and cost-pool truth.");
    }
  }

  /** Builds deterministic replay details for one raw byte-seed input. */
  static InventoryCostingMathReplayDetails replayDetails(byte[] input) {
    Objects.requireNonNull(input, "input must not be null");
    long seed = InventoryCostingScenarioSupport.seedFrom(input);
    InventoryCostingScenarioSupport.ScenarioSample knownProjectionMismatch =
        InventoryCostingScenarioSupport.knownProjectionMismatchScenario();
    InventoryCostingScenarioSupport.ScenarioSample sample =
        InventoryCostingScenarioSupport.seedDerivedProjectionMismatchScenario(seed);
    return new InventoryCostingMathReplayDetails(
        seed,
        SCENARIO_COUNT,
        projectionMismatchInvariantHolds(
            knownProjectionMismatch.exactCostOfSales(),
            knownProjectionMismatch.projectionBasedCostOfSales(),
            knownProjectionMismatch.disposal()),
        projectionMismatchInvariantHolds(
            sample.exactCostOfSales(), sample.projectionBasedCostOfSales(), sample.disposal()),
        new InventoryCostingPoolDetails(
            sample.pool().quantityOnHand().canonicalDecimal(),
            sample.pool().quantityOnHand().scaledUnits(),
            sample.pool().quantityOnHand().scale(),
            sample.pool().costPool().canonicalDecimal(),
            sample.pool().costPool().minorUnits()),
        new InventoryCostingExerciseDetails(
            sample.disposedQuantity().canonicalDecimal(),
            sample.projection().canonicalDecimal(),
            sample.exactCostOfSales().canonicalDecimal(),
            sample.projectionBasedCostOfSales().canonicalDecimal(),
            sample.disposal().remainingPool().quantityOnHand().canonicalDecimal(),
            sample.disposal().remainingPool().costPool().canonicalDecimal()));
  }

  private static void assertKnownProjectionMismatchCase() {
    InventoryCostingScenarioSupport.ScenarioSample sample =
        InventoryCostingScenarioSupport.knownProjectionMismatchScenario();
    JazzerInvariantAssertionSupport.require(
        projectionMismatchInvariantHolds(
            sample.exactCostOfSales(), sample.projectionBasedCostOfSales(), sample.disposal()),
        "Known weighted-average mismatch case must keep exact pool math distinct from one rounded projection-based surrogate.");
    JazzerInvariantAssertionSupport.require(
        knownProjectionMismatchCaseHolds(
            sample.exactCostOfSales(), sample.projectionBasedCostOfSales()),
        "Known weighted-average mismatch case must keep exact pool math distinct from one rounded projection-based surrogate.");
  }

  static boolean exactPoolMathInvariantHolds(
      Money exactCostOfSales, WeightedAverageCostingMath.Disposal disposal) {
    return disposal.costOfSales().equals(exactCostOfSales);
  }

  static boolean zeroToZeroInvariantHolds(Quantity quantityOnHand, Money costPool) {
    return quantityOnHand.isZero() == costPool.isZero();
  }

  static boolean knownProjectionMismatchCaseHolds(
      Money exactCostOfSales, Money projectionBasedCost) {
    return exactCostOfSales.equals(Money.ofMinorUnits(EUR, 3L))
        && projectionBasedCost.equals(Money.ofMinorUnits(EUR, 2L));
  }

  static boolean projectionMismatchInvariantHolds(
      Money exactCostOfSales,
      Money projectionBasedCostOfSales,
      WeightedAverageCostingMath.Disposal disposal) {
    return exactPoolMathInvariantHolds(exactCostOfSales, disposal)
        && !disposal.costOfSales().equals(projectionBasedCostOfSales);
  }
}
