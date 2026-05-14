package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.Objects;

/** Local bookkeeping criteria for one bounded period-summary view. */
public record PeriodSummaryCriteria(
    LocalDate effectiveDateFrom, LocalDate effectiveDateTo, PostingCoverage postingCoverage) {
  public PeriodSummaryCriteria {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }

  /** Convenience constructor that defaults to all posting kinds. */
  public PeriodSummaryCriteria(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    this(effectiveDateFrom, effectiveDateTo, PostingCoverage.ALL_POSTING_KINDS);
  }
}
