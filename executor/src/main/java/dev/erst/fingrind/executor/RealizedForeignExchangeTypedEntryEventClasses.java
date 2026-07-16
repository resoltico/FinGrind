package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Realized foreign-exchange entry kinds and their owned event classes. */
final class RealizedForeignExchangeTypedEntryEventClasses {
  private RealizedForeignExchangeTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        EconomicEventClass.FOREIGN_CURRENCY_OBLIGATION,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        EconomicEventClass.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT);
  }
}
