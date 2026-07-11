package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import java.util.List;

/** Loads the exact inventory movements durably linked to one committed posting. */
public interface InventoryMovementLookupStore {
  /** Returns every inventory movement durably linked to the selected posting in replay order. */
  default List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    return List.of();
  }
}
