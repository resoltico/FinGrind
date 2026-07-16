package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Inventory maintenance entry kinds and their owned event classes. */
final class InventoryTypedEntryEventClasses {
  private InventoryTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        EconomicEventClass.INVENTORY_CAPITALIZATION,
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        EconomicEventClass.INVENTORY_CAPITALIZATION,
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        EconomicEventClass.INVENTORY_WRITE_DOWN,
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        EconomicEventClass.INVENTORY_SHRINKAGE,
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        EconomicEventClass.INVENTORY_COUNT_INCREASE);
  }
}
