package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Executor-owned resolved inventory effects for one posting request. */
public record InventoryPostingResolution(
    BookkeepingEntry resolvedEntry,
    List<InventoryMovementRecord> inventoryMovements,
    Map<AccountCode, InventoryAccountState> resultingInventoryStates) {
  /** Validates one resolved inventory posting payload. */
  public InventoryPostingResolution {
    Objects.requireNonNull(resolvedEntry, "resolvedEntry");
    inventoryMovements =
        List.copyOf(Objects.requireNonNull(inventoryMovements, "inventoryMovements"));
    resultingInventoryStates =
        Map.copyOf(Objects.requireNonNull(resultingInventoryStates, "resultingInventoryStates"));
  }

  /** Returns one resolution that does not touch inventory state. */
  public static InventoryPostingResolution withoutInventory(BookkeepingEntry resolvedEntry) {
    return new InventoryPostingResolution(resolvedEntry, List.of(), Map.of());
  }
}
