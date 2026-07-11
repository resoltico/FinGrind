package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Read port for canonical inventory-ledger replay used by inventory valuation. */
@FunctionalInterface
public interface InventoryValuationStore {
  /**
   * Returns ordered durable inventory movements through the selected inclusive effective-date
   * cutoff.
   */
  List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf);
}
