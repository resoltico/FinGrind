package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.ComparativeSelection;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** As-of query for one statement of financial position. */
public record FinancialPositionQuery(
    Optional<LocalDate> effectiveDateAsOf, ComparativeSelection comparativeSelection) {
  /** Validates one financial-position query. */
  public FinancialPositionQuery {
    Objects.requireNonNull(effectiveDateAsOf, "effectiveDateAsOf");
    comparativeSelection =
        ComparativeSelection.requireAsOfCompatible(comparativeSelection, "comparativeSelection");
  }
}
