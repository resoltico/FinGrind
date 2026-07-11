package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.Quantity;
import java.util.Objects;

/**
 * Executor-owned exact inventory-acquisition resolution retained on quantity-increasing entries.
 */
public record ResolvedInventoryAcquisition(
    Quantity quantityAcquired, MonetaryAmount preTaxCost, MonetaryAmount carryingCost) {
  /** Validates one resolved inventory-acquisition payload. */
  public ResolvedInventoryAcquisition {
    Objects.requireNonNull(quantityAcquired, "quantityAcquired");
    Objects.requireNonNull(preTaxCost, "preTaxCost");
    Objects.requireNonNull(carryingCost, "carryingCost");
    if (!quantityAcquired.isPositive()) {
      throw new IllegalArgumentException("quantityAcquired must be positive.");
    }
    if (!preTaxCost.toMoney().isPositive()) {
      throw new IllegalArgumentException("preTaxCost must be positive.");
    }
    if (!carryingCost.toMoney().isPositive()) {
      throw new IllegalArgumentException("carryingCost must be positive.");
    }
    if (!preTaxCost.currencyCode().equals(carryingCost.currencyCode())) {
      throw new IllegalArgumentException("preTaxCost and carryingCost must share one currency.");
    }
    if (carryingCost.toMoney().minorUnits() < preTaxCost.toMoney().minorUnits()) {
      throw new IllegalArgumentException("carryingCost must not be less than preTaxCost.");
    }
  }
}
