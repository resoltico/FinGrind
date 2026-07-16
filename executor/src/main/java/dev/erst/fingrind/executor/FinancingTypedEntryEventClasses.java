package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.EconomicEventClass;
import java.util.Map;

/** Financing entry kinds and their owned event classes. */
final class FinancingTypedEntryEventClasses {
  private FinancingTypedEntryEventClasses() {}

  static Map<BookkeepingEntryKind, EconomicEventClass> eventClasses() {
    return Map.of(
        BookkeepingEntryKind.FINANCING_BORROWING,
        EconomicEventClass.FINANCING_BORROWING,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        EconomicEventClass.FINANCING_PRINCIPAL_REPAYMENT,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        EconomicEventClass.FINANCING_INTEREST_ACCRUAL,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        EconomicEventClass.FINANCING_INTEREST_PAYMENT);
  }
}
