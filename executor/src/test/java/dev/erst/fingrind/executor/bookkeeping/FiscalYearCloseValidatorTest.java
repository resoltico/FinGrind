package dev.erst.fingrind.executor.bookkeeping;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.ReportingPeriod;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Direct coverage for fiscal-year-close validation overloads and horizon ordering. */
class FiscalYearCloseValidatorTest {
  private static final ReportingPeriod FISCAL_YEAR_2026 =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));

  @Test
  void rejectionFor_localDateAndClockOverloadsAcceptAlignedFiscalYear() {
    assertTrue(
        FiscalYearCloseValidator.rejectionFor(
                FISCAL_YEAR_2026, bookIdentity(), LocalDate.parse("2026-12-31"))
            .isEmpty());
    assertTrue(
        FiscalYearCloseValidator.rejectionFor(
                FISCAL_YEAR_2026,
                bookIdentity(),
                Clock.fixed(Instant.parse("2026-12-31T12:00:00Z"), ZoneOffset.UTC))
            .isEmpty());
  }

  @Test
  void rejectionFor_rejectsFiscalYearCloseThatPrecedesTransferredThroughHorizon() {
    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.FiscalYearClosePrecedesTransferredThroughHorizon(
                LocalDate.parse("2026-12-31"), LocalDate.parse("2027-03-31"))),
        FiscalYearCloseValidator.rejectionFor(
            FISCAL_YEAR_2026,
            bookIdentity(),
            LocalDate.parse("2027-03-31"),
            Optional.of(LocalDate.parse("2027-03-31"))));
    assertTrue(
        FiscalYearCloseValidator.rejectionFor(
                FISCAL_YEAR_2026,
                bookIdentity(),
                LocalDate.parse("2026-12-31"),
                Optional.of(LocalDate.parse("2026-12-31")))
            .isEmpty());
  }

  @Test
  void rejectionFor_rejectsFutureAndMisalignedFiscalYearBoundaries() {
    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.FiscalYearCloseFutureDate(
                LocalDate.parse("2026-12-31"))),
        FiscalYearCloseValidator.rejectionFor(
            FISCAL_YEAR_2026, bookIdentity(), LocalDate.parse("2026-12-30")));
    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-01-01"))),
        FiscalYearCloseValidator.rejectionFor(
            new ReportingPeriod(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-12-31")),
            bookIdentity(),
            LocalDate.parse("2026-12-31")));
    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustEndAt(
                LocalDate.parse("2026-12-31"))),
        FiscalYearCloseValidator.rejectionFor(
            new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-11-30")),
            bookIdentity(),
            LocalDate.parse("2026-12-31")));
  }

  @Test
  void rejectionFor_acceptsThePartialFirstFiscalYearSegmentAtImmutableBookStart() {
    dev.erst.fingrind.core.BookIdentity baseline = bookIdentity();
    dev.erst.fingrind.core.BookIdentity midYearBook =
        new dev.erst.fingrind.core.BookIdentity(
            baseline.entityProfile(),
            baseline.bookDoctrine(),
            baseline.functionalCurrency(),
            baseline.fiscalYearStart(),
            LocalDate.parse("2026-07-01"));
    ReportingPeriod firstFiscalYearSegment =
        new ReportingPeriod(LocalDate.parse("2026-07-01"), LocalDate.parse("2026-12-31"));

    assertTrue(
        FiscalYearCloseValidator.rejectionFor(
                firstFiscalYearSegment, midYearBook, LocalDate.parse("2026-12-31"))
            .isEmpty());
  }

  @Test
  void rejectionFor_rejectsAFiscalYearThatEndedBeforeTheImmutableBookStart() {
    dev.erst.fingrind.core.BookIdentity baseline = bookIdentity();
    dev.erst.fingrind.core.BookIdentity midYearBook =
        new dev.erst.fingrind.core.BookIdentity(
            baseline.entityProfile(),
            baseline.bookDoctrine(),
            baseline.functionalCurrency(),
            baseline.fiscalYearStart(),
            LocalDate.parse("2026-07-01"));

    assertEquals(
        Optional.of(
            new BookkeepingAdministrationRejection.FiscalYearCloseMustStartAt(
                LocalDate.parse("2026-07-01"))),
        FiscalYearCloseValidator.rejectionFor(
            new ReportingPeriod(LocalDate.parse("2025-01-01"), LocalDate.parse("2025-12-31")),
            midYearBook,
            LocalDate.parse("2026-12-31")));
  }
}
