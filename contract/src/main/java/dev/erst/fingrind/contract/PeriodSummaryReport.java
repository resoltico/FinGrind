package dev.erst.fingrind.contract;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

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
  public PeriodSummaryReport {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
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
    currencyTotals = List.copyOf(Objects.requireNonNull(currencyTotals, "currencyTotals"));
    accountActivity = List.copyOf(Objects.requireNonNull(accountActivity, "accountActivity"));
  }
}
