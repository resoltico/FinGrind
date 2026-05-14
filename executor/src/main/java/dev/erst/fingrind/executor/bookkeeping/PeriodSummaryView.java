package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.PostingCoverage;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Local bookkeeping bounded period-summary view. */
public record PeriodSummaryView(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    PostingCoverage postingCoverage,
    int postingCount,
    int postingLineCount,
    int accountsTouched,
    List<PeriodCurrencySummaryView> currencySummaries,
    List<PeriodAccountActivityView> accountActivity) {
  public PeriodSummaryView {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    Objects.requireNonNull(postingCoverage, "postingCoverage");
    Objects.requireNonNull(currencySummaries, "currencySummaries");
    Objects.requireNonNull(accountActivity, "accountActivity");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    if (postingCount < 0) {
      throw new IllegalArgumentException("postingCount must be non-negative.");
    }
    if (postingLineCount < 0) {
      throw new IllegalArgumentException("postingLineCount must be non-negative.");
    }
    if (accountsTouched < 0) {
      throw new IllegalArgumentException("accountsTouched must be non-negative.");
    }
    currencySummaries = List.copyOf(currencySummaries);
    accountActivity = List.copyOf(accountActivity);
  }
}
