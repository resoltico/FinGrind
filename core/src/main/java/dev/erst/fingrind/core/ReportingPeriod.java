package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.util.Objects;

/** One closed calendar-like reporting period bounded by inclusive effective dates. */
public record ReportingPeriod(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
  /** Validates one inclusive reporting period. */
  public ReportingPeriod {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    if (effectiveDateFrom.isAfter(effectiveDateTo)) {
      throw new IllegalArgumentException("effectiveDateFrom must be on or before effectiveDateTo.");
    }
  }

  /** Returns this reporting period as the structurally typed effective-date range. */
  public EffectiveDateRange effectiveDateRange() {
    return EffectiveDateRange.of(effectiveDateFrom, effectiveDateTo);
  }

  /** Returns whether the supplied effective date falls inside this inclusive period. */
  public boolean contains(LocalDate effectiveDate) {
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    return !effectiveDate.isBefore(effectiveDateFrom) && !effectiveDate.isAfter(effectiveDateTo);
  }

  /** Returns the effective date immediately after the inclusive period end. */
  public LocalDate dayAfter() {
    return effectiveDateTo.plusDays(1);
  }
}
