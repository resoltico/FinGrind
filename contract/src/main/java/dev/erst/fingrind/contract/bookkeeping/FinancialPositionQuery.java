package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** As-of query for one statement of financial position. */
public record FinancialPositionQuery(Optional<LocalDate> effectiveDateTo) {
  /** Validates one financial-position query. */
  public FinancialPositionQuery {
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
  }
}
