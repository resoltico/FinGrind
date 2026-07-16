package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Inclusive interval in which one prepayment or deferred-revenue amount may be recognized. */
public record AccrualCutoffRecognitionInterval(LocalDate startDate, LocalDate endDate) {
  /** Validates one inclusive recognition interval. */
  public AccrualCutoffRecognitionInterval {
    Objects.requireNonNull(startDate, "startDate");
    Objects.requireNonNull(endDate, "endDate");
    if (endDate.isBefore(startDate)) {
      throw new IllegalArgumentException(
          "Accrual cut-off recognition endDate must not precede startDate.");
    }
  }

  /** Returns whether the selected date is inside this inclusive interval. */
  public boolean contains(LocalDate effectiveDate) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    return !effectiveDate.isBefore(startDate) && !effectiveDate.isAfter(endDate);
  }
}
