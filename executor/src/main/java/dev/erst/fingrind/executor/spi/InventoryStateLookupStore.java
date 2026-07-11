package dev.erst.fingrind.executor.spi;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.executor.bookkeeping.InventoryAccountState;
import java.util.Optional;

/** Looks up exact inventory on-hand state by declared inventory account. */
public interface InventoryStateLookupStore {
  /**
   * Returns the current exact inventory state for one inventory account, if any movement exists.
   */
  default Optional<InventoryAccountState> findInventoryAccountState(
      AccountCode inventoryAccountCode) {
    return Optional.empty();
  }
}
