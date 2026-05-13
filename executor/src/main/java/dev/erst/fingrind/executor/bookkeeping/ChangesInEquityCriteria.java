package dev.erst.fingrind.executor.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping criteria for one bounded statement of changes in equity. */
public record ChangesInEquityCriteria(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  public ChangesInEquityCriteria {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
