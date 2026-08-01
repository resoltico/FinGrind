package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct branch coverage for SQLite close-read helpers. */
class SqliteClosingMutationReadSupportCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod FISCAL_YEAR_2026 =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Clock CLOSED_CLOCK =
      Clock.fixed(Instant.parse("2026-12-31T23:59:59Z"), ZoneOffset.UTC);

  @Test
  void loadFiscalYearClose_collectsAllPostingIdsFromThePostingLinkQuery() {
    Path bookPath = tempDirectory.resolve("closing-read-support-posting-ids.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareAllCloseTargets(postingFactStore);
      assertInstanceOf(
          PostingCommitResult.Appended.class,
          commitPosting(
              postingFactStore,
              postingFact(
                  "close-read-revenue",
                  "close-read-revenue-idempotency",
                  LocalDate.parse("2026-12-31"),
                  Instant.parse("2026-12-31T12:00:00Z"),
                  List.of(
                      line("1000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "10.00"),
                      line(
                          "2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "10.00")))));
      Iterator<PostingId> generatedPostingIds =
          List.of(
                  new PostingId("b244b900-4f37-3bc5-8921-71e87988c768"),
                  new PostingId("400a867a-e8bc-3b8f-a362-4c7991b8e7c0"),
                  new PostingId("f2d6b8e9-39e5-3979-85c7-77ea755bc401"))
              .iterator();

      FiscalYearCloseOutcome.Closed closed =
          assertInstanceOf(
              FiscalYearCloseOutcome.Closed.class,
              postingFactStore
                  .storeClosingMutationOperations()
                  .fiscalYearClose(
                      FISCAL_YEAR_2026,
                      bookIdentity(),
                      directClosePlanner(),
                      LocalDate.now(CLOSED_CLOCK),
                      CLOSED_CLOCK.instant(),
                      generatedPostingIds::next,
                      SqliteAttestationTestSupport.authorizer()));

      SqliteClosingMutationReadSupport readSupport =
          new SqliteClosingMutationReadSupport(postingFactStore.storeContext());
      try (SqliteNativeDatabase redirectedDatabase =
          redirectedDatabase(
              requireStoreDatabase(postingFactStore),
              SqliteReportingPeriodCloseSql.FIND_FISCAL_YEAR_CLOSE_POSTING_IDS,
              """
              select '1dd6c0b8-8a55-384b-8d9a-f9dadfefb140' where ?1 is not null
              union all
              select '08e947c3-7bf0-35b9-b5dd-596eca04cc4c' where ?1 is not null
              """)) {
        ClosedFiscalYearRecord loaded =
            readSupport.loadFiscalYearClose(redirectedDatabase, FISCAL_YEAR_2026).orElseThrow();

        assertEquals(closed.closedFiscalYear().closeOrder(), loaded.closeOrder());
        assertEquals(closed.closedFiscalYear().reportingPeriod(), loaded.reportingPeriod());
        assertEquals(closed.closedFiscalYear().capitalAccountCode(), loaded.capitalAccountCode());
        assertEquals(
            closed.closedFiscalYear().resultHoldingAccountCode(),
            loaded.resultHoldingAccountCode());
        assertEquals(
            closed.closedFiscalYear().retainedAccumulatedAccountCode(),
            loaded.retainedAccumulatedAccountCode());
        assertEquals(closed.closedFiscalYear().closedAt(), loaded.closedAt());
        assertEquals(
            List.of(
                new PostingId("1dd6c0b8-8a55-384b-8d9a-f9dadfefb140"),
                new PostingId("08e947c3-7bf0-35b9-b5dd-596eca04cc4c")),
            loaded.closePostingIds());
      }
    }
  }

  @Test
  void loadFiscalYearClose_rejectsDuplicateCloseRows() {
    Path bookPath = tempDirectory.resolve("closing-read-support-duplicate-close.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      SqliteClosingMutationReadSupport readSupport =
          new SqliteClosingMutationReadSupport(postingFactStore.storeContext());
      try (SqliteNativeDatabase redirectedDatabase =
          redirectedDatabase(
              requireStoreDatabase(postingFactStore),
              SqliteReportingPeriodCloseSql.FIND_FISCAL_YEAR_CLOSE_BY_PERIOD,
              """
              select 1, '3000', '3200', '3300', '2026-12-31T23:59:59Z'
              where ?1 is not null and ?2 is not null
              union all
              select 1, '3000', '3200', '3300', '2026-12-31T23:59:59Z'
              where ?1 is not null and ?2 is not null
              """)) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> readSupport.loadFiscalYearClose(redirectedDatabase, FISCAL_YEAR_2026));

        assertEquals(
            "SQLite fiscal-year close query returned more than one row for one reporting period.",
            failure.getMessage());
      }
    }
  }

  private static SqliteStatementRedirectingDatabase redirectedDatabase(
      SqliteNativeDatabase database, String targetSql, String replacementSql) {
    return new SqliteStatementRedirectingDatabase(
        database, sql -> database.prepare(targetSql.equals(sql) ? replacementSql : sql));
  }

  private static FiscalYearClosePlanner directClosePlanner() {
    return FiscalYearClosePlanner.forBookIdentity(bookIdentity());
  }

  private static void declareAllCloseTargets(SqlitePostingFactStore postingFactStore) {
    declareCloseAccount(
        postingFactStore,
        "3000",
        "Capital",
        FinancialPositionLineClassification.EQUITY_CONTRIBUTION);
    declareCloseAccount(
        postingFactStore,
        "3200",
        "Result Holding",
        FinancialPositionLineClassification.RESULT_HOLDING);
    declareCloseAccount(
        postingFactStore,
        "3300",
        "Retained Accumulated",
        FinancialPositionLineClassification.RETAINED_ACCUMULATED);
  }

  private static void declareCloseAccount(
      SqlitePostingFactStore postingFactStore,
      String accountCode,
      String accountName,
      FinancialPositionLineClassification classification) {
    assertDeclaredWithAttestation(
        new RegisteredAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            AccountType.EQUITY,
            financialPositionTaxonomy(classification),
            true,
            CLOSED_CLOCK.instant()),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode(accountCode),
                new AccountName(accountName),
                AccountType.EQUITY,
                financialPositionTaxonomy(classification)),
            CLOSED_CLOCK.instant(),
            SqliteAttestationTestSupport.authorizer()));
  }
}
