package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Accrual cut-off entry kinds and their owned event classes. */
final class AccrualCutoffTypedEntryEventClasses {
  private AccrualCutoffTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.PREPAYMENT,
        EconomicEventClass.PREPAYMENT,
        BookkeepingEntryKind.DEFERRED_REVENUE,
        EconomicEventClass.DEFERRED_REVENUE,
        BookkeepingEntryKind.ACCRUED_EXPENSE,
        EconomicEventClass.ACCRUED_EXPENSE,
        BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
        EconomicEventClass.ACCRUAL_CUTOFF_RECOGNITION,
        BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
        EconomicEventClass.ACCRUED_EXPENSE_SETTLEMENT);
  }
}
