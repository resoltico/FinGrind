package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;

/** Required bounded period request for one cash-flow statement. */
public record CashFlowStatementQuery(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    ComparativeSelection comparativeSelection) {
  /** Validates one cash-flow-statement query. */
  public CashFlowStatementQuery {
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
