package dev.erst.fingrind.jazzer.tool;

import java.util.Objects;

/** Stable replay details for committed inventory-costing math seeds. */
public record InventoryCostingMathReplayDetails(
    long seed,
    int exercisedScenarioCount,
    boolean knownProjectionMismatchCaseProven,
    boolean seedDerivedProjectionMismatchCaseProven,
    InventoryCostingPoolDetails samplePool,
    InventoryCostingExerciseDetails sampleExercise)
    implements ReplayDetails {
  public InventoryCostingMathReplayDetails {
    Objects.requireNonNull(samplePool, "samplePool must not be null");
    Objects.requireNonNull(sampleExercise, "sampleExercise must not be null");
  }
}

/** Deterministic sample pool facts projected from one replayed inventory-costing seed. */
record InventoryCostingPoolDetails(
    String quantityOnHand,
    long quantityScaledUnits,
    int quantityScale,
    String costPool,
    long costPoolMinorUnits) {
  InventoryCostingPoolDetails {
    Objects.requireNonNull(quantityOnHand, "quantityOnHand must not be null");
    Objects.requireNonNull(costPool, "costPool must not be null");
  }
}

/** Deterministic sample exercise facts projected from one replayed inventory-costing seed. */
record InventoryCostingExerciseDetails(
    String disposedQuantity,
    String roundedProjection,
    String exactCostOfSales,
    String projectionBasedCostOfSales,
    String remainingQuantityOnHand,
    String remainingCostPool) {
  InventoryCostingExerciseDetails {
    Objects.requireNonNull(disposedQuantity, "disposedQuantity must not be null");
    Objects.requireNonNull(roundedProjection, "roundedProjection must not be null");
    Objects.requireNonNull(exactCostOfSales, "exactCostOfSales must not be null");
    Objects.requireNonNull(
        projectionBasedCostOfSales, "projectionBasedCostOfSales must not be null");
    Objects.requireNonNull(remainingQuantityOnHand, "remainingQuantityOnHand must not be null");
    Objects.requireNonNull(remainingCostPool, "remainingCostPool must not be null");
  }
}
