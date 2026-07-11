package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;

/**
 * Resolves caller quantity text against the declared unit scale with canonical rejection language.
 */
final class InventoryQuantityResolution {
  private InventoryQuantityResolution() {}

  static Quantity resolve(
      String field,
      String quantityText,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure,
      QuantityResolver resolver) {
    try {
      return resolver.resolve();
    } catch (UnitOfMeasure.QuantityIncompatibleWithUnitOfMeasureException exception) {
      throw new InventoryEntrySemanticsFailure(
          InventoryEntrySemanticsViolations.inventoryQuantityIncompatibleWithUnitOfMeasure(
              field, quantityText, inventoryAccountCode, unitOfMeasure, exception.reason()),
          exception);
    }
  }

  /** Defers parsing until the caller's inventory unit-of-measure is available. */
  @FunctionalInterface
  interface QuantityResolver {
    /** Resolves one exact quantity at the declared unit scale. */
    Quantity resolve();
  }
}
