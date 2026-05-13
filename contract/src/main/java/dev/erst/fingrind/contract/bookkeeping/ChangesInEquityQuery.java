package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Required bounded period request for one statement of changes in equity. */
public record ChangesInEquityQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  /** Validates one changes-in-equity query. */
  public ChangesInEquityQuery {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }
}
