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
    var parsed = CanonicalTemporalText.parseMonthDay(wireValue, "fiscalYearStart");
    return new FiscalYearStart(parsed.getMonthValue(), parsed.getDayOfMonth());
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

  /** Returns the fiscal-year start date for the fiscal year identified by the selected label. */
  public LocalDate labeledFiscalYearStart(int fiscalYearLabel) {
    return resolvedStartDate(fiscalYearLabel);
  }

  /** Returns whether the bounded period lies wholly inside one fiscal year. */
  public boolean containsSingleFiscalYear(LocalDate effectiveDateFrom, LocalDate effectiveDateTo) {
    Objects.requireNonNull(effectiveDateFrom, "effectiveDateFrom");
    Objects.requireNonNull(effectiveDateTo, "effectiveDateTo");
    return containingFiscalYearStart(effectiveDateFrom)
        .equals(containingFiscalYearStart(effectiveDateTo));
  }

  /** Returns the fiscal-year end date for the fiscal year that contains the supplied date. */
  public LocalDate containingFiscalYearEnd(LocalDate date) {
    Objects.requireNonNull(date, "date");
    LocalDate fiscalYearStartDate = containingFiscalYearStart(date);
    return resolvedStartDate(fiscalYearStartDate.getYear() + 1).minusDays(1);
  }

  /** Returns the fiscal-year end date for the fiscal year identified by the selected label. */
  public LocalDate labeledFiscalYearEnd(int fiscalYearLabel) {
    return resolvedStartDate(fiscalYearLabel + 1).minusDays(1);
  }

  private LocalDate resolvedStartDate(int year) {
    int maxDay = YearMonth.of(year, month).lengthOfMonth();
    return LocalDate.of(year, month, Math.min(day, maxDay));
  }

  /** Returns the canonical {@code MM-DD} wire shape for this fiscal-year anchor. */
  public String wireValue() {
    return CanonicalTemporalText.formatMonthDay(monthDay());
  }

  @Override
  public String toString() {
    return wireValue();
  }
}
