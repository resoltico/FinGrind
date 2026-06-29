package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;

/** Required bounded period request for one income statement. */
public record IncomeStatementQuery(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection) {
  /** Validates one income-statement query. */
  public IncomeStatementQuery {
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
