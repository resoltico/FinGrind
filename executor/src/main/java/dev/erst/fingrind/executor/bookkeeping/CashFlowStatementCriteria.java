package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping criteria for one bounded cash-flow statement. */
public record CashFlowStatementCriteria(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection) {
  public CashFlowStatementCriteria {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    comparativeSelection =
        ComparativeSelection.requireBoundedPeriodCompatible(
            comparativeSelection, "comparativeSelection");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
