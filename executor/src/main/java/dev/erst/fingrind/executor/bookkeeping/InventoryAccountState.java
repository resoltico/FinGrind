package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Exact on-hand inventory state plus the per-account movement horizon. */
public record InventoryAccountState(
    WeightedAverageCostingMath.InventoryPool pool, Optional<LocalDate> lastMovementDate) {
  /** Validates one inventory account state snapshot. */
  public InventoryAccountState {
    Objects.requireNonNull(pool, "pool");
    Objects.requireNonNull(lastMovementDate, "lastMovementDate");
  }
}
