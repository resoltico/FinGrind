package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping criteria for one bounded period-summary view. */
public record PeriodSummaryCriteria(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  public PeriodSummaryCriteria {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
