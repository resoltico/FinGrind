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
      BookIdentity bookIdentity,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    LocalDate effectiveDateFrom =
        requiredEffectiveDateFrom(bookIdentity, transferredThroughEffectiveDate);
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
    LocalDate requiredEffectiveDateFrom =
        requiredEffectiveDateFrom(bookIdentity, transferredThroughEffectiveDate);
    return requiredEffectiveDateFrom.equals(reportingPeriod.effectiveDateFrom())
        ? Optional.empty()
        : Optional.of(
            new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
                requiredEffectiveDateFrom));
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      LocalDate throughEffectiveDate,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return closeHorizonRejection(
        throughEffectiveDate,
        bookIdentity,
        java.time.Clock.fixed(
            currentUtcDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC),
        transferredThroughEffectiveDate);
  }

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      LocalDate throughEffectiveDate,
      BookIdentity bookIdentity,
      Clock clock,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(throughEffectiveDate, "throughEffectiveDate");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    LocalDate requiredEffectiveDateFrom =
        requiredEffectiveDateFrom(bookIdentity, transferredThroughEffectiveDate);
    if (requiredEffectiveDateFrom.isAfter(throughEffectiveDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.InterimResultSweepMustStartAt(
              requiredEffectiveDateFrom));
    }
    return closeHorizonRejection(
        reportingPeriodFor(throughEffectiveDate, bookIdentity, transferredThroughEffectiveDate),
        bookIdentity,
        clock,
        transferredThroughEffectiveDate);
  }

  private static LocalDate requiredEffectiveDateFrom(
      BookIdentity bookIdentity, Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    return transferredThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .orElse(bookIdentity.bookStartEffectiveDate());
  }
}
