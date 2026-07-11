package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import java.util.Objects;

/** Executor-owned carrying-cost resolution for a non-sale inventory disposal. */
public record ResolvedInventoryDisposal(
    Money carryingCost, Quantity quantityDisposed, Money roundedMovingAverageUnitCostProjection) {
  /** Validates one resolved non-sale inventory disposal payload. */
  public ResolvedInventoryDisposal {
    Objects.requireNonNull(carryingCost, "carryingCost");
    Objects.requireNonNull(quantityDisposed, "quantityDisposed");
    Objects.requireNonNull(
        roundedMovingAverageUnitCostProjection, "roundedMovingAverageUnitCostProjection");
    if (!carryingCost.isPositive()) {
      throw new IllegalArgumentException("carryingCost must be positive.");
    }
    if (!quantityDisposed.isPositive()) {
      throw new IllegalArgumentException("quantityDisposed must be positive.");
    }
    if (!carryingCost
        .currencyUnit()
        .equals(roundedMovingAverageUnitCostProjection.currencyUnit())) {
      throw new IllegalArgumentException(
          "roundedMovingAverageUnitCostProjection must share carryingCost currency.");
    }
  }
}
