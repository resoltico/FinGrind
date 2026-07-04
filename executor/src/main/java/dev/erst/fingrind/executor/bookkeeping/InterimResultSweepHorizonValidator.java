package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateHorizonPolicy;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates whether one reporting period may be transferred at the current close horizon. */
final class InterimResultSweepHorizonValidator {
  private InterimResultSweepHorizonValidator() {}

  static ReportingPeriod reportingPeriodFor(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(bookStartDate, "bookStartDate");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    LocalDate effectiveDateFrom =
        requiredEffectiveDateFrom(bookStartDate, transferredThroughEffectiveDate);
    return new ReportingPeriod(effectiveDateFrom, throughEffectiveDate);
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return closeHorizonRejection(
        reportingPeriod,
        bookIdentity,
        java.time.Clock.fixed(
            currentUtcDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC),
        transferredThroughEffectiveDate);
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      Clock clock,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(clock, "clock");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    try {
      EffectiveDateHorizonPolicy.requireNotAfterToday(reportingPeriod.effectiveDateTo(), clock);
    } catch (EffectiveDateHorizonPolicy.FutureEffectiveDateException exception) {
      return Optional.of(
          new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
              exception.attemptedEffectiveDate()));
    }
    if (!bookIdentity
        .fiscalYearStart()
        .containsSingleFiscalYear(
            reportingPeriod.effectiveDateFrom(), reportingPeriod.effectiveDateTo())) {
      return Optional.of(
          new BookkeepingAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary(
              reportingPeriod.effectiveDateFrom(),
              reportingPeriod.effectiveDateTo(),
              bookIdentity.fiscalYearStart()));
    }
    return transferredThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .filter(requiredStart -> !requiredStart.equals(reportingPeriod.effectiveDateFrom()))
        .<BookkeepingAdministrationRejection>map(
            BookkeepingAdministrationRejection.InterimResultSweepMustStartAt::new);
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return closeHorizonRejection(
        throughEffectiveDate,
        bookStartDate,
        bookIdentity,
        java.time.Clock.fixed(
            currentUtcDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC),
        transferredThroughEffectiveDate);
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      LocalDate throughEffectiveDate,
      LocalDate bookStartDate,
      BookIdentity bookIdentity,
      Clock clock,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(bookStartDate, "bookStartDate");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    LocalDate requiredEffectiveDateFrom =
        requiredEffectiveDateFrom(bookStartDate, transferredThroughEffectiveDate);
    if (requiredEffectiveDateFrom.isAfter(throughEffectiveDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
              requiredEffectiveDateFrom));
    }
    return closeHorizonRejection(
        reportingPeriodFor(
            throughEffectiveDate, bookStartDate, bookIdentity, transferredThroughEffectiveDate),
        bookIdentity,
        clock,
        transferredThroughEffectiveDate);
  }

  private static LocalDate requiredEffectiveDateFrom(
      LocalDate bookStartDate, Optional<LocalDate> transferredThroughEffectiveDate) {
    return transferredThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .orElse(bookStartDate);
  }
}
