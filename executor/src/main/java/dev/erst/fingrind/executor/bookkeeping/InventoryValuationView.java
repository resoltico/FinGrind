package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Local exact valuation of one inventory account after canonical movement replay. */
public record InventoryValuationView(
    RegisteredAccount account,
    WeightedAverageCostingMath.InventoryPool pool,
    @Nullable Money roundedMovingAverageUnitCostProjection,
    List<InventoryValuationMovementRecord> movements) {
  /** Validates an exact valuation view and its non-authoritative unit-cost projection. */
  public InventoryValuationView {
    Objects.requireNonNull(account, "account");
    if (account.unitOfMeasure() == null) {
      throw new IllegalArgumentException("Inventory valuation requires one inventory account.");
    }
    Objects.requireNonNull(pool, "pool");
    Quantity quantity = pool.quantityOnHand();
    account.unitOfMeasure().requireCompatible(quantity);
    if (quantity.isZero() != (roundedMovingAverageUnitCostProjection == null)) {
      throw new IllegalArgumentException(
          "A rounded unit-cost projection is required exactly when quantity is positive.");
    }
    movements = List.copyOf(Objects.requireNonNull(movements, "movements"));
  }
}
