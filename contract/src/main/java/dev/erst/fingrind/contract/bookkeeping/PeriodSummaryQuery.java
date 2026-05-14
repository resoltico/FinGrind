package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;

/** Required bounded period request for one book-wide summary report. */
public record PeriodSummaryQuery(
    LocalDate effectiveDateFrom, LocalDate effectiveDateTo, PostingCoverage postingCoverage) {
  /** Validates one period-summary query. */
  public PeriodSummaryQuery {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }

  /** Convenience constructor that defaults to all posting kinds. */
  public PeriodSummaryQuery(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    this(effectiveDateFrom, effectiveDateTo, PostingCoverage.ALL_POSTING_KINDS);
  }
}
