package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.PostingId;
import java.time.LocalDate;
import java.util.Objects;

/** One canonical-replay inventory movement read from the durable inventory subledger. */
public record InventoryValuationMovementRecord(
    AccountCode inventoryAccount,
    LocalDate effectiveDate,
    long accountSequence,
    InventoryMovementKind kind,
    long quantityDelta,
    long costDeltaMinor,
    PostingId postingId) {
  /** Validates one ordered inventory movement ledger fact. */
  public InventoryValuationMovementRecord {
    Objects.requireNonNull(inventoryAccount, "inventoryAccount");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    if (accountSequence <= 0L) {
      throw new IllegalArgumentException("accountSequence must be positive.");
    }
    Objects.requireNonNull(kind, "kind");
    if (quantityDelta == 0L && costDeltaMinor == 0L) {
      throw new IllegalArgumentException(
          "Inventory valuation movements must change quantity or cost.");
    }
    Objects.requireNonNull(postingId, "postingId");
  }
}
