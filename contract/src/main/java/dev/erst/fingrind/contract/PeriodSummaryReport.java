package dev.erst.fingrind.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Canonical bounded period summary for one selected book. */
public record PeriodSummaryReport(
    LocalDate effectiveDateFrom,
    LocalDate effectiveDateTo,
    int postingCount,
    int postingLineCount,
    int accountsTouched,
    List<PeriodCurrencySummary> currencyTotals,
    List<PeriodAccountActivityRow> accountActivity) {
  /** Validates one period summary report. */
  public PeriodSummaryReport(
      LocalDate effectiveDateFrom,
      LocalDate effectiveDateTo,
      int postingCount,
      int postingLineCount,
      int accountsTouched,
      @Nullable List<PeriodCurrencySummary> currencyTotals,
      @Nullable List<PeriodAccountActivityRow> accountActivity) {
    this.effectiveDateFrom = Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    this.effectiveDateTo = Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    this.postingCount = postingCount;
    this.postingLineCount = postingLineCount;
    this.accountsTouched = accountsTouched;
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
    if (postingCount < 0) {
      throw new IllegalArgumentException("postingCount must not be negative.");
    }
    if (postingLineCount < 0) {
      throw new IllegalArgumentException("postingLineCount must not be negative.");
    }
    if (accountsTouched < 0) {
      throw new IllegalArgumentException("accountsTouched must not be negative.");
    }
    this.currencyTotals = currencyTotals == null ? List.of() : List.copyOf(currencyTotals);
    this.accountActivity = accountActivity == null ? List.of() : List.copyOf(accountActivity);
  }
}
