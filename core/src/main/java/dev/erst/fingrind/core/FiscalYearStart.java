package dev.erst.fingrind.core;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.util.Objects;

/** Month-day anchor that defines when one book's fiscal year begins. */
public record FiscalYearStart(int month, int day) {
  /** Validates one fiscal-year month-day anchor. */
  public FiscalYearStart {
    MonthDay.of(month, day);
  }

  /** Parses one canonical {@code MM-DD} fiscal-year anchor. */
  public static FiscalYearStart parse(String wireValue) {
    Objects.requireNonNull(wireValue, "wireValue");
    if (!wireValue.matches("^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$")) {
      throw new IllegalArgumentException("Fiscal year start must use MM-DD.");
    }
    return new FiscalYearStart(
        Integer.parseInt(wireValue.substring(0, 2)), Integer.parseInt(wireValue.substring(3, 5)));
  }

  /** Returns the validated month-day anchor as one JDK value object. */
  public MonthDay monthDay() {
    return MonthDay.of(month, day);
  }

  /** Returns the fiscal-year start date for the fiscal year that contains the supplied date. */
  public LocalDate containingFiscalYearStart(LocalDate date) {
    Objects.requireNonNull(date, "date");
    LocalDate anchorInSameYear = resolvedStartDate(date.getYear());
    return date.isBefore(anchorInSameYear)
        ? resolvedStartDate(date.getYear() - 1)
        : anchorInSameYear;
  }

  /** Returns whether the bounded period lies wholly inside one fiscal year. */
  public boolean containsSingleFiscalYear(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    return containingFiscalYearStart(effectiveDateFrom)
        .equals(containingFiscalYearStart(effectiveDateTo));
  }

  private LocalDate resolvedStartDate(int year) {
    int maxDay = YearMonth.of(year, month).lengthOfMonth();
    return LocalDate.of(year, month, Math.min(day, maxDay));
  }

  /** Returns the canonical {@code MM-DD} wire shape for this fiscal-year anchor. */
  public String wireValue() {
    return "%02d-%02d".formatted(month, day);
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
