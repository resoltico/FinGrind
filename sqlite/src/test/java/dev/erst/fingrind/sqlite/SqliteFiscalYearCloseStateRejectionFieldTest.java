package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.FiscalYearCloseService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclaration;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Field-tests fiscal-year-close admission before any durable closing facts are written. */
class SqliteFiscalYearCloseStateRejectionFieldTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Instant CLOSED_AT = Instant.parse("2027-01-01T00:00:00Z");

  @Test
  void close_rejectsMissingAndUninitializedBooksAtTheDurableOperationBoundary() {
    try (SqlitePostingFactStore missingStore =
            openStore(bookAccess(tempDirectory.resolve("missing-close-book.sqlite")));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(missingStore)) {
      assertRejected(closeSession, LocalDate.parse("2027-01-01"));
    }

    Path blankBookPath = tempDirectory.resolve("blank-close-book.sqlite");
    dev.erst.fingrind.contract.runtime.BookAccess blankBookAccess = bookAccess(blankBookPath);
    try (SqliteNativeDatabase ignored = openNativeDatabase(blankBookAccess)) {
      // Establish a valid encrypted SQLite file without the FinGrind initialization record.
    }
    try (SqlitePostingFactStore blankStore = openStore(blankBookAccess);
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(blankStore)) {
      assertRejected(closeSession, LocalDate.parse("2027-01-01"));
    }
  }

  @Test
  void close_rejectsPrematureWindowsAndEachMissingRequiredCloseTargetInOrder() {
    Path bookPath = tempDirectory.resolve("close-target-admission.sqlite");
    BookIdentity identity = SqlitePostingFactFixtureSupport.bookIdentity();
    try (SqlitePostingFactStore store = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(store)) {
      store.openAttestedBook(
          Instant.parse("2026-01-01T00:00:00Z"),
          identity,
          List.of(),
          SqliteAttestationTestSupport.genesis(identity, Instant.parse("2026-01-01T00:00:00Z")));

      assertRejected(closeSession, LocalDate.parse("2026-06-30"));
      assertRejected(closeSession, LocalDate.parse("2027-01-01"));

      declareTarget(
          store, "3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
      assertRejected(closeSession, LocalDate.parse("2027-01-01"));

      declareTarget(
          store, "3200", "Result holding", FinancialPositionLineClassification.RESULT_HOLDING);
      assertRejected(closeSession, LocalDate.parse("2027-01-01"));

      declareTarget(
          store,
          "3300",
          "Retained accumulated",
          FinancialPositionLineClassification.RETAINED_ACCUMULATED);
      FiscalYearCloseOutcome.Rejected rejected =
          assertInstanceOf(
              FiscalYearCloseOutcome.Rejected.class,
              close(closeSession, LocalDate.parse("2027-01-01")));
      assertInstanceOf(
          BookkeepingAdministrationRejection.FiscalYearCloseRequiresGeneratedPostings.class,
          rejected.rejection());
    }
  }

  private static void assertRejected(
      SqliteReportingPeriodCloseSession closeSession, LocalDate currentUtcDate) {
    assertInstanceOf(FiscalYearCloseOutcome.Rejected.class, close(closeSession, currentUtcDate));
  }

  private static FiscalYearCloseOutcome close(
      SqliteReportingPeriodCloseSession closeSession, LocalDate currentUtcDate) {
    Clock clock =
        Clock.fixed(currentUtcDate.atTime(12, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    return new FiscalYearCloseService(
            () ->
                SqlitePostingFactFixtureSupport.initializedLifecycleInspection(
                    SqliteBookContract.APPLICATION_ID,
                    SqliteBookContract.FORMAT_VERSION,
                    SqliteBookContract.FORMAT_VERSION,
                    CLOSED_AT),
            closeSession,
            () -> new PostingId("f2a59de9-9d48-4a10-b4ba-716460242876"),
            clock)
        .fiscalYearClose(FISCAL_YEAR, SqliteAttestationTestSupport.authorizer());
  }

  private static void declareTarget(
      SqlitePostingFactStore store,
      String accountCode,
      String accountName,
      FinancialPositionLineClassification classification) {
    store.declareAccount(
        new AccountDeclaration(
            new AccountCode(accountCode),
            new AccountName(accountName),
            AccountType.EQUITY,
            SqlitePostingFactFixtureSupport.financialPositionTaxonomy(classification)),
        CLOSED_AT,
        SqliteAttestationTestSupport.authorizer());
  }
}
