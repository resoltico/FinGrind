package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Latvian payroll entry kinds and their owned event classes. */
final class LatvianPayrollTypedEntryEventClasses {
  private LatvianPayrollTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
        EconomicEventClass.LATVIAN_MONTHLY_PAYROLL,
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        EconomicEventClass.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
        EconomicEventClass.LATVIAN_PAYROLL_STATE_REMITTANCE);
  }
}
