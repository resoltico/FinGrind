package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Coverage for the through-date interim-result-sweep overload across the SQLite close surface. */
class SqliteInterimResultSweepThroughDateCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC);
  private static final Instant SWEEPED_AT = Instant.parse("2026-04-30T10:15:30Z");
  private static final Clock SWEEP_CLOCK = Clock.fixed(SWEEPED_AT, ZoneOffset.UTC);

  @Test
  void interimResultSweep_throughDateOverloadRejectsFutureDatesAtTheSessionBoundary() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-through-date.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      postingFactStore.declareAccount(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
              new AccountCode("3200"),
              new AccountName("Result Holding"),
              AccountType.EQUITY,
              financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
          Instant.parse("2026-12-31T12:00:00Z"),
          SqliteAttestationTestSupport.authorizer());

      InterimResultSweepOutcome.Rejected rejected =
          assertInstanceOf(
              InterimResultSweepOutcome.Rejected.class,
              new InterimResultSweepService(
                      closeSession,
                      closeSession,
                      () -> new PostingId("unused-posting-id"),
                      FIXED_CLOCK)
                  .interimResultSweep(
                      LocalDate.parse("2027-01-01"), SqliteAttestationTestSupport.authorizer()));

      assertEquals(
          new BookkeepingAdministrationRejection.InterimResultSweepFutureDate(
              LocalDate.parse("2027-01-01")),
          rejected.rejection());
    }
  }

  @Test
  void interimResultSweep_throughDateOverloadTransfersTheDerivedWindowAtTheSessionBoundary() {
    Path bookPath = tempDirectory.resolve("interim-result-sweep-through-date-success.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      postingFactStore.declareAccount(
          new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
              new AccountCode("3200"),
              new AccountName("Result Holding"),
              AccountType.EQUITY,
              financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
          SWEEPED_AT,
          SqliteAttestationTestSupport.authorizer());
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-1",
              "idem-1",
              LocalDate.parse("2026-04-09"),
              SWEEPED_AT,
              java.util.List.of(
                  line("1000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "10.00"))));

      InterimResultSweepOutcome.Transferred transferred =
          assertInstanceOf(
              InterimResultSweepOutcome.Transferred.class,
              new InterimResultSweepService(
                      closeSession,
                      closeSession,
                      () -> new PostingId("generated-sweep-1"),
                      SWEEP_CLOCK)
                  .interimResultSweep(
                      LocalDate.parse("2026-04-09"), SqliteAttestationTestSupport.authorizer()));

      assertEquals(1, transferred.sweptInterimResult().sweepOrder());
      assertEquals(
          new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-04-09")),
          transferred.sweptInterimResult().reportingPeriod());
      assertEquals(
          Optional.of(LocalDate.parse("2026-04-09")),
          postingFactStore.transferredThroughEffectiveDate());
    }
  }
}
