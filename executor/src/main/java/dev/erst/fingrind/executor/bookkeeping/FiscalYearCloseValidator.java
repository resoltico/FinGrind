package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.EffectiveDateHorizonPolicy;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates whether one reporting period is admissible for fiscal-year close. */
final class FiscalYearCloseValidator {
  private FiscalYearCloseValidator() {}

  static Optional<BookkeepingAdministrationRejection> rejectionFor(
      ReportingPeriod reportingPeriod, BookIdentity bookIdentity, LocalDate currentUtcDate) {
    return rejectionFor(
        reportingPeriod,
        bookIdentity,
        java.time.Clock.fixed(
            currentUtcDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC));
  }

  static Optional<BookkeepingAdministrationRejection> rejectionFor(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    return rejectionFor(
        reportingPeriod,
        bookIdentity,
        java.time.Clock.fixed(
            currentUtcDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
            java.time.ZoneOffset.UTC),
        transferredThroughEffectiveDate);
  }

  static Optional<BookkeepingAdministrationRejection> rejectionFor(
      ReportingPeriod reportingPeriod, BookIdentity bookIdentity, Clock clock) {
    return rejectionFor(reportingPeriod, bookIdentity, clock, Optional.empty());
  }

  static Optional<BookkeepingAdministrationRejection> rejectionFor(
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
          new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
              exception.attemptedEffectiveDate()));
    }
    LocalDate fiscalYearStart =
        bookIdentity
            .fiscalYearStart()
            .containingFiscalYearStart(reportingPeriod.effectiveDateFrom());
    LocalDate fiscalYearEnd =
        bookIdentity.fiscalYearStart().containingFiscalYearEnd(reportingPeriod.effectiveDateFrom());
    LocalDate requiredEffectiveDateFrom =
        bookIdentity.bookStartEffectiveDate().isAfter(fiscalYearStart)
            ? bookIdentity.bookStartEffectiveDate()
            : fiscalYearStart;
    if (!reportingPeriod.effectiveDateFrom().equals(requiredEffectiveDateFrom)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
              requiredEffectiveDateFrom));
    }
    LocalDate requiredEffectiveDateTo = fiscalYearEnd;
    if (!reportingPeriod.effectiveDateTo().equals(requiredEffectiveDateTo)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(requiredEffectiveDateTo));
    }
    return transferredThroughEffectiveDate
        .filter(closedThrough -> reportingPeriod.effectiveDateTo().isBefore(closedThrough))
        .<BookkeepingAdministrationRejection>map(
            closedThrough ->
                new BookkeepingAdministrationRejection
                    .FiscalYearClosePrecedesTransferredThroughHorizon(
                    reportingPeriod.effectiveDateTo(), closedThrough));
  }
}
