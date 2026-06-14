package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates whether one reporting period may be transferred at the current close horizon. */
final class PeriodResultTransferCloseHorizonValidator {
  private PeriodResultTransferCloseHorizonValidator() {}

  static Optional<BookkeepingAdministrationRejection> closeHorizonRejection(
      ReportingPeriod reportingPeriod,
      BookIdentity bookIdentity,
      LocalDate currentUtcDate,
      Optional<LocalDate> transferredThroughEffectiveDate) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    Objects.requireNonNull(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    if (reportingPeriod.effectiveDateTo().isAfter(currentUtcDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodResultTransferFutureDate(
              reportingPeriod.effectiveDateTo()));
    }
    if (!bookIdentity
        .fiscalYearStart()
        .containsSingleFiscalYear(
            reportingPeriod.effectiveDateFrom(), reportingPeriod.effectiveDateTo())) {
      return Optional.of(
          new BookkeepingAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary(
              reportingPeriod.effectiveDateFrom(),
              reportingPeriod.effectiveDateTo(),
              bookIdentity.fiscalYearStart()));
    }
    return transferredThroughEffectiveDate
        .map(closedThrough -> closedThrough.plusDays(1))
        .filter(requiredStart -> !requiredStart.equals(reportingPeriod.effectiveDateFrom()))
        .<BookkeepingAdministrationRejection>map(
            BookkeepingAdministrationRejection.PeriodResultTransferMustStartAt::new);
  }
}
