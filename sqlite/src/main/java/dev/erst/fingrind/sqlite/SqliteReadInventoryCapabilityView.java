package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import dev.erst.fingrind.executor.bookkeeping.InventoryMovementRecord;
import dev.erst.fingrind.executor.bookkeeping.InventoryValuationMovementRecord;
import dev.erst.fingrind.executor.spi.InventoryMovementLookupStore;
import dev.erst.fingrind.executor.spi.InventoryStateLookupStore;
import dev.erst.fingrind.executor.spi.InventoryValuationStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Inventory state, movement, and valuation defaults for SQLite read wrappers. */
interface SqliteReadInventoryCapabilityView
    extends InventoryMovementLookupStore,
        InventoryStateLookupStore,
        InventoryValuationStore,
        SqlitePostingFactStoreReadOperationsView,
        SqliteInventoryValuationReadOperationsView {
  @Override
  default Optional<InventoryAccountState> findInventoryAccountState(
      AccountCode inventoryAccountCode) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inventory().findInventoryAccountState(inventoryAccountCode);
  }

  @Override
  default List<InventoryMovementRecord> inventoryMovements(PostingId postingId) {
    storeThreadOwner().requireOwnerThread();
    return storeReadOperations().inventory().inventoryMovements(postingId);
  }

  @Override
  default List<InventoryValuationMovementRecord> inventoryValuationMovements(
      Optional<LocalDate> effectiveDateAsOf) {
    storeThreadOwner().requireOwnerThread();
    return storeInventoryValuationReadOperations().inventoryValuationMovements(effectiveDateAsOf);
  }
}
