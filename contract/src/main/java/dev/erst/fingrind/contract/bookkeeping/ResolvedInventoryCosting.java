package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.util.Objects;

/** Executor-owned exact inventory-disposal costing facts retained on one sale entry. */
public record ResolvedInventoryCosting(
    Money costOfSales, Quantity quantityRelieved, Money roundedMovingAverageUnitCostProjection) {
  /** Validates one resolved inventory-costing payload. */
  public ResolvedInventoryCosting {
    Objects.requireNonNull(costOfSales, "costOfSales");
    Objects.requireNonNull(quantityRelieved, "quantityRelieved");
    Objects.requireNonNull(
        roundedMovingAverageUnitCostProjection, "roundedMovingAverageUnitCostProjection");
    if (!costOfSales.isPositive()) {
      throw new IllegalArgumentException("costOfSales must be positive.");
    }
    if (!quantityRelieved.isPositive()) {
      throw new IllegalArgumentException("quantityRelieved must be positive.");
    }
    if (!roundedMovingAverageUnitCostProjection.isPositive()) {
      throw new IllegalArgumentException(
          "roundedMovingAverageUnitCostProjection must be positive.");
    }
  }
}
