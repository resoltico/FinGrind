package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.FiscalYearCloseService;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.ClosedFiscalYearRecord;
import dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Focused SQLite coverage for fiscal-year-close persistence, links, and close sequencing. */
class SqliteFiscalYearCloseCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final ReportingPeriod FISCAL_YEAR =
      new ReportingPeriod(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
  private static final Instant CLOSED_AT = Instant.parse("2026-12-31T23:59:59Z");
  private static final Clock CLOSED_CLOCK = Clock.fixed(CLOSED_AT, ZoneOffset.UTC);

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void fiscalYearClose_storeBoundaryPersistsGeneratedSweepCloseFactsAndLifecycleIntegrity() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-direct.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareFiscalYearCloseAccounts(postingFactStore);
      commitFiscalYearActivity(postingFactStore);

      FiscalYearCloseOutcome.Closed closed =
          assertInstanceOf(
              FiscalYearCloseOutcome.Closed.class,
              new FiscalYearCloseService(
                      () ->
                          initializedLifecycleInspection(
                              SqliteBookContract.APPLICATION_ID,
                              SqliteBookContract.FORMAT_VERSION,
                              SqliteBookContract.FORMAT_VERSION,
                              Instant.parse("2026-04-07T10:15:30Z")),
                      new ReportingPeriodCloseStore() {
                        @Override
                        public java.util.List<
                                dev.erst.fingrind.executor.bookkeeping.CommittedPosting>
                            postings(dev.erst.fingrind.core.EffectiveDateRange effectiveDateRange) {
                          return java.util.List.of();
                        }

                        @Override
                        public java.util.Optional<LocalDate> earliestPostingEffectiveDate() {
                          return java.util.Optional.empty();
                        }

                        @Override
                        public java.util.Optional<LocalDate> transferredThroughEffectiveDate() {
                          return java.util.Optional.empty();
                        }

                        @Override
                        public InterimResultSweepOutcome interimResultSweep(
                            ReportingPeriod reportingPeriod,
                            dev.erst.fingrind.core.BookIdentity bookIdentity,
                            dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner
                                planner,
                            LocalDate currentUtcDate,
                            Instant sweptAt,
                            PostingIdGenerator postingIdGenerator,
                            dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                                attestationAuthorizer) {
                          throw new UnsupportedOperationException(
                              "Interim-result sweep is not under test here.");
                        }

                        @Override
                        public InterimResultSweepOutcome interimResultSweep(
                            LocalDate throughEffectiveDate,
                            LocalDate bookStartDate,
                            dev.erst.fingrind.core.BookIdentity bookIdentity,
                            dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner
                                planner,
                            LocalDate currentUtcDate,
                            Instant sweptAt,
                            PostingIdGenerator postingIdGenerator,
                            dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                                attestationAuthorizer) {
                          throw new UnsupportedOperationException(
                              "Interim-result sweep is not under test here.");
                        }

                        @Override
                        public FiscalYearCloseOutcome fiscalYearClose(
                            ReportingPeriod reportingPeriod,
                            dev.erst.fingrind.core.BookIdentity bookIdentity,
                            dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner planner,
                            LocalDate currentUtcDate,
                            Instant closedAt,
                            PostingIdGenerator postingIdGenerator,
                            dev.erst.fingrind.core.attestation.AttestationOperationAuthorizer
                                attestationAuthorizer) {
                          return postingFactStore.fiscalYearClose(
                              reportingPeriod,
                              bookIdentity,
                              planner,
                              currentUtcDate,
                              closedAt,
                              postingIdGenerator,
                              attestationAuthorizer);
                        }
                      },
                      new SequencePostingIdGenerator(
                          "generated-sweep-1", "generated-close-1", "generated-close-2"),
                      CLOSED_CLOCK)
                  .fiscalYearClose(FISCAL_YEAR, SqliteAttestationTestSupport.authorizer()));

      assertEquals(
          new ClosedFiscalYearRecord(
              1,
              FISCAL_YEAR,
              new AccountCode("3000"),
              new AccountCode("3200"),
              new AccountCode("3300"),
              CLOSED_AT,
              List.of(new PostingId("05dff89b-fb24-3b4f-a8eb-e522d8af750e"), new PostingId("6d593e82-86bd-3ca6-bdf6-3e8b7976791d"))),
          closed.closedFiscalYear());
      assertEquals(
          Optional.of(FISCAL_YEAR.effectiveDateTo()),
          postingFactStore.transferredThroughEffectiveDate());

      SqliteNativeDatabase database = requireStoreDatabase(postingFactStore);
      assertEquals(1, countRows(database, "interim_result_sweep"));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              database, "posting_fact", "posting_kind", "INTERIM_RESULT_SWEEP"));
      assertEquals(1, countRows(database, "fiscal_year_close"));
      assertEquals(
          2,
          countRowsWhereTextEquals(database, "posting_fact", "posting_kind", "FISCAL_YEAR_CLOSE"));
      assertEquals(1, countRows(database, "interim_result_sweep_posting"));
      assertEquals(2, countRows(database, "fiscal_year_close_posting"));
      assertEquals("3000|3200|3300", fiscalYearCloseTargetCodes(database));
      assertEquals(
          1,
          countRowsWhereTextEquals(database, "audit_event", "event_kind", "INTERIM_RESULT_SWEPT"));
      assertEquals(
          1, countRowsWhereTextEquals(database, "audit_event", "event_kind", "FISCAL_YEAR_CLOSED"));
      assertTrue(SqliteBookIntegrityVerifier.hasValidPersistedPostingLifecycle(database));
    }
  }

  @Test
  @SuppressWarnings("PMD.CloseResource")
  void fiscalYearClose_sessionBoundarySkipsGeneratingASecondSweepWhenAlreadyClosedThroughYearEnd() {
    Path bookPath = tempDirectory.resolve("fiscal-year-close-session.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(bookPath));
        SqliteReportingPeriodCloseSession closeSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      declareFiscalYearCloseAccounts(postingFactStore);
      commitFiscalYearActivity(postingFactStore);

      assertInstanceOf(
          InterimResultSweepOutcome.Transferred.class,
          new InterimResultSweepService(
                  closeSession,
                  closeSession,
                  new SequencePostingIdGenerator("generated-sweep-1"),
                  CLOSED_CLOCK)
              .interimResultSweep(FISCAL_YEAR, SqliteAttestationTestSupport.authorizer()));

      FiscalYearCloseOutcome.Closed closed =
          assertInstanceOf(
              FiscalYearCloseOutcome.Closed.class,
              new FiscalYearCloseService(
                      closeSession,
                      closeSession,
                      new SequencePostingIdGenerator("generated-close-1", "generated-close-2"),
                      CLOSED_CLOCK)
                  .fiscalYearClose(FISCAL_YEAR, SqliteAttestationTestSupport.authorizer()));

      assertEquals(
          List.of(new PostingId("05dff89b-fb24-3b4f-a8eb-e522d8af750e"), new PostingId("6d593e82-86bd-3ca6-bdf6-3e8b7976791d")),
          closed.closedFiscalYear().closePostingIds());

      SqliteNativeDatabase database = requireStoreDatabase(postingFactStore);
      assertEquals(1, countRows(database, "interim_result_sweep"));
      assertEquals(
          1,
          countRowsWhereTextEquals(
              database, "posting_fact", "posting_kind", "INTERIM_RESULT_SWEEP"));
      assertEquals(
          1,
          countRowsWhereTextEquals(database, "audit_event", "event_kind", "INTERIM_RESULT_SWEPT"));
      assertEquals(
          1, countRowsWhereTextEquals(database, "audit_event", "event_kind", "FISCAL_YEAR_CLOSED"));
      assertEquals(2, countRows(database, "fiscal_year_close_posting"));
    }
  }

  private static void declareFiscalYearCloseAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        declaredEquityAccount(
            "3000", "Capital", FinancialPositionLineClassification.EQUITY_CONTRIBUTION),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode("3000"),
                new AccountName("Capital"),
                AccountType.EQUITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.EQUITY_CONTRIBUTION)),
            CLOSED_AT,
            SqliteAttestationTestSupport.authorizer()));
    assertEquals(
        declaredEquityAccount(
            "3100", "Owner Draw", FinancialPositionLineClassification.EQUITY_WITHDRAWAL),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode("3100"),
                new AccountName("Owner Draw"),
                AccountType.EQUITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.EQUITY_WITHDRAWAL)),
            CLOSED_AT,
            SqliteAttestationTestSupport.authorizer()));
    assertEquals(
        declaredEquityAccount(
            "3200", "Result Holding", FinancialPositionLineClassification.RESULT_HOLDING),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode("3200"),
                new AccountName("Result Holding"),
                AccountType.EQUITY,
                financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING)),
            CLOSED_AT,
            SqliteAttestationTestSupport.authorizer()));
    assertEquals(
        declaredEquityAccount(
            "3300",
            "Retained Accumulated",
            FinancialPositionLineClassification.RETAINED_ACCUMULATED),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode("3300"),
                new AccountName("Retained Accumulated"),
                AccountType.EQUITY,
                financialPositionTaxonomy(
                    FinancialPositionLineClassification.RETAINED_ACCUMULATED)),
            CLOSED_AT,
            SqliteAttestationTestSupport.authorizer()));
    assertEquals(
        new AccountDeclarationOutcome.Declared(
            new RegisteredAccount(
                new AccountCode("5000"),
                new AccountName("Operating Expense"),
                AccountType.EXPENSE,
                accountTaxonomy(AccountType.EXPENSE),
                true,
                CLOSED_AT)),
        postingFactStore.declareAccount(
            new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                new AccountCode("5000"),
                new AccountName("Operating Expense"),
                AccountType.EXPENSE,
                accountTaxonomy(AccountType.EXPENSE)),
            CLOSED_AT,
            SqliteAttestationTestSupport.authorizer()));
  }

  private static void commitFiscalYearActivity(SqlitePostingFactStore postingFactStore) {
    commitPosting(
        postingFactStore,
        postingFact(
            "sale-posting",
            "sale-idem",
            LocalDate.parse("2026-12-31"),
            Instant.parse("2026-12-31T10:00:00Z"),
            List.of(
                line("1000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "120.00"),
                line("2000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "120.00"))));
    commitPosting(
        postingFactStore,
        postingFact(
            "expense-posting",
            "expense-idem",
            LocalDate.parse("2026-12-31"),
            Instant.parse("2026-12-31T11:00:00Z"),
            List.of(
                line("5000", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "45.00"),
                line("1000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "45.00"))));
    commitPosting(
        postingFactStore,
        postingFact(
            "draw-posting",
            "draw-idem",
            LocalDate.parse("2026-12-31"),
            Instant.parse("2026-12-31T12:00:00Z"),
            List.of(
                line("3100", dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT, "10.00"),
                line("1000", dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT, "10.00"))));
  }

  private static AccountDeclarationOutcome declaredEquityAccount(
      String accountCode, String accountName, FinancialPositionLineClassification classification) {
    return new AccountDeclarationOutcome.Declared(
        new RegisteredAccount(
            new AccountCode(accountCode),
            new AccountName(accountName),
            AccountType.EQUITY,
            financialPositionTaxonomy(classification),
            true,
            CLOSED_AT));
  }

  /** Deterministic posting-id source for fiscal-year-close coverage paths. */
  private static final class SequencePostingIdGenerator implements PostingIdGenerator {
    private final String[] postingIds;
    private int nextIndex;

    private SequencePostingIdGenerator(String... postingIds) {
      this.postingIds = postingIds;
    }

    @Override
    public PostingId nextPostingId() {
      if (nextIndex >= postingIds.length) {
        throw new IllegalStateException("SequencePostingIdGenerator ran out of posting ids.");
      }
      int postingIndex = nextIndex;
      nextIndex++;
      return new PostingId(java.util.UUID.nameUUIDFromBytes(("fingrind-test-postingid:" + postingIds[postingIndex]).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
    }
  }
}
