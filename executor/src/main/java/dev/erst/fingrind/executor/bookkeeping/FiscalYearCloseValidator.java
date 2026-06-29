package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.ReportingPeriod;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Validates whether one reporting period is admissible for fiscal-year close. */
final class FiscalYearCloseValidator {
  private FiscalYearCloseValidator() {}

  static Optional<BookkeepingAdministrationRejection> rejectionFor(
      ReportingPeriod reportingPeriod, BookIdentity bookIdentity, LocalDate currentUtcDate) {
    Objects.requireNonNull(reportingPeriod, "reportingPeriod");
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(currentUtcDate, "currentUtcDate");
    if (reportingPeriod.effectiveDateTo().isAfter(currentUtcDate)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
              reportingPeriod.effectiveDateTo()));
    }
    LocalDate requiredEffectiveDateFrom =
        bookIdentity
            .fiscalYearStart()
            .containingFiscalYearStart(reportingPeriod.effectiveDateFrom());
    if (!reportingPeriod.effectiveDateFrom().equals(requiredEffectiveDateFrom)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
              requiredEffectiveDateFrom));
    }
    LocalDate requiredEffectiveDateTo =
        bookIdentity.fiscalYearStart().containingFiscalYearEnd(reportingPeriod.effectiveDateFrom());
    if (!reportingPeriod.effectiveDateTo().equals(requiredEffectiveDateTo)) {
      return Optional.of(
          new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(requiredEffectiveDateTo));
    }
    return Optional.empty();
  }
}
