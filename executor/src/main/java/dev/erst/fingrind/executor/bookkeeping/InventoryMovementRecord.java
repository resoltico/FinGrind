package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import java.time.LocalDate;
import java.util.Objects;

/** One exact inventory movement over one inventory account at one posting effective date. */
public record InventoryMovementRecord(
    AccountCode inventoryAccount,
    LocalDate effectiveDate,
    InventoryMovementKind kind,
    long quantityDelta,
    long costDeltaMinor) {
  /** Validates one inventory movement record. */
  public InventoryMovementRecord {
    Objects.requireNonNull(inventoryAccount, "inventoryAccount");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(kind, "kind");
    if (quantityDelta == 0L && costDeltaMinor == 0L) {
      throw new IllegalArgumentException(
          "Inventory movement records must change quantity, carrying cost, or both.");
    }
  }
}
