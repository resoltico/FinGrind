package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping criteria for one bounded statement of changes in equity. */
public record ChangesInEquityCriteria(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection) {
  public ChangesInEquityCriteria {
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
