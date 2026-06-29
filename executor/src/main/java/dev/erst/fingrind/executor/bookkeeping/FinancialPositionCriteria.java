package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping criteria for one as-of statement of financial position. */
public record FinancialPositionCriteria(
    Optional<LocalDate> effectiveDateAsOf, ComparativeSelection comparativeSelection) {
  public FinancialPositionCriteria {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    comparativeSelection =
        ComparativeSelection.requireAsOfCompatible(comparativeSelection, "comparativeSelection");
  }
}
