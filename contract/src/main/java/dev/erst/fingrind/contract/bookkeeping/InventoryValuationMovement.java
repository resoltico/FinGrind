package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;

/** One ordered durable movement included in an inventory-valuation detail projection. */
public record InventoryValuationMovement(
    PostingId postingId,
    LocalDate effectiveDate,
    long accountSequence,
    InventoryMovementKind kind,
    long quantityDeltaScaledUnits,
    long costDeltaMinor) {
  /** Validates one published movement detail. */
  public InventoryValuationMovement {
    Objects.requireNonNull(postingId, "postingId");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    if (accountSequence <= 0L) {
      throw new IllegalArgumentException("accountSequence must be positive.");
    }
    Objects.requireNonNull(kind, "kind");
    if (quantityDeltaScaledUnits == 0L && costDeltaMinor == 0L) {
      throw new IllegalArgumentException(
          "Inventory valuation movement must change quantity or cost.");
    }
  }
}
