package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Standard business-event entry kinds and their owned event classes. */
final class StandardTypedEntryEventClasses {
  private StandardTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.ofEntries(
        Map.entry(BookkeepingEntryKind.SALE_SETTLED, EconomicEventClass.SETTLED_SALE),
        Map.entry(BookkeepingEntryKind.SALE_ON_CREDIT, EconomicEventClass.CREDIT_SALE),
        Map.entry(BookkeepingEntryKind.PURCHASE_SETTLED, EconomicEventClass.SETTLED_PURCHASE),
        Map.entry(BookkeepingEntryKind.PURCHASE_ON_CREDIT, EconomicEventClass.CREDIT_PURCHASE),
        Map.entry(BookkeepingEntryKind.EXPENSE_SETTLED, EconomicEventClass.SETTLED_EXPENSE),
        Map.entry(BookkeepingEntryKind.EXPENSE_ON_CREDIT, EconomicEventClass.CREDIT_EXPENSE),
        Map.entry(BookkeepingEntryKind.RECEIPT, EconomicEventClass.AR_SETTLEMENT),
        Map.entry(BookkeepingEntryKind.PAYMENT, EconomicEventClass.AP_SETTLEMENT),
        Map.entry(BookkeepingEntryKind.OWNER_CONTRIBUTION, EconomicEventClass.OWNER_CONTRIBUTION),
        Map.entry(BookkeepingEntryKind.OWNER_WITHDRAWAL, EconomicEventClass.OWNER_WITHDRAWAL),
        Map.entry(BookkeepingEntryKind.OPENING_POSITION, EconomicEventClass.OPENING),
        Map.entry(BookkeepingEntryKind.REVERSAL, EconomicEventClass.REVERSAL));
  }
}
