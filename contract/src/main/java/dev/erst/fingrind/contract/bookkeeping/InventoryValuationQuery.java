package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Point-in-time request for the exact carrying value of every inventory account. */
public record InventoryValuationQuery(
    Optional<LocalDate> effectiveDateAsOf, boolean includeMovements) {
  /** Validates one inventory-valuation request. */
  public InventoryValuationQuery {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
  }
}
