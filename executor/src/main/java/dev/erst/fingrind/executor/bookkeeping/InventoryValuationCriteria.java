package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Local criteria for a point-in-time inventory valuation. */
public record InventoryValuationCriteria(
    Optional<LocalDate> effectiveDateAsOf, boolean includeMovements) {
  /** Validates local valuation criteria. */
  public InventoryValuationCriteria {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
  }
}
