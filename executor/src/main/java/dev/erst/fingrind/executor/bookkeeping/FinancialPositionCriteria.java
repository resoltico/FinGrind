package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Local bookkeeping criteria for one as-of statement of financial position. */
public record FinancialPositionCriteria(Optional<LocalDate> effectiveDateTo) {
  public FinancialPositionCriteria {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
  }
}
