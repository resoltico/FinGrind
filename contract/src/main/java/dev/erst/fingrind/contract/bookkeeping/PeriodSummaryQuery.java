package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Required bounded period request for one book-wide summary report. */
public record PeriodSummaryQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  /** Validates one period-summary query. */
  public PeriodSummaryQuery {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
