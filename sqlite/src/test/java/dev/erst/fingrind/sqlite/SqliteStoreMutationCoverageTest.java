package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PostingIdGenerator;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Focused residual coverage for SQLite mutation-session failure and rejection paths. */
class SqliteStoreMutationCoverageTest extends SqlitePostingFactStoreTestSupport {
  private static final LocalDate PERIOD_DATE = LocalDate.parse("2026-04-07");
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-30T10:15:30Z");

  @Test
  void declareAccount_rollsBackRuntimeFailuresAfterTheTransactionBegins() {
    Path databasePath = tempDirectory.resolve("declare-runtime-rollback.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(FIXED_INSTANT, bookIdentity(), List.of());
      AtomicReference<SqliteNativeDatabase> realDatabase =
          new AtomicReference<>(requireStoreDatabase(postingFactStore));
      setStoreDatabase(
          postingFactStore,
          new SqliteStatementRedirectingDatabase(
              realDatabase.get(),
              sql -> {
                if (SqlitePostingSql.INSERT_AUDIT_EVENT.equals(sql)) {
                  throw new IllegalStateException("forced account audit failure");
                }
                return realDatabase.get().prepare(sql);
              }));

      try {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore.declareAccount(
                        new AccountCode("1000"),
                        new AccountName("Cash"),
                        AccountType.ASSET,
                        accountTaxonomy(AccountType.ASSET),
                        FIXED_INSTANT));
        assertEquals("forced account audit failure", failure.getMessage());
        assertEquals(0, queryInt(realDatabase.get(), "select count(*) from account"));
        assertEquals(
            0,
            queryInt(
                realDatabase.get(),
                """
                select count(*)
                from audit_event
                where event_kind in ('ACCOUNT_DECLARED', 'ACCOUNT_REACTIVATED')
                """));
      } finally {
        setStoreDatabase(postingFactStore, realDatabase.get());
      }
    }
  }

  @Test
  void declareAccount_renamesActiveAccountsWithoutMarkingThemAsReactivated() {
    Path databasePath = tempDirectory.resolve("declare-active-redeclare.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      openBookWithNoDeclaredAccounts(postingFactStore);
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          dev.erst.fingrind.core.NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new AccountDeclarationOutcome.Renamed(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  AccountType.ASSET,
                  dev.erst.fingrind.core.NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash main"),
              AccountType.ASSET,
              dev.erst.fingrind.core.NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          "ACCOUNT_RENAMED",
          queryText(
              requireStoreDatabase(postingFactStore),
              """
              select event_kind
              from audit_event
              order by audit_event_order desc
              limit 1
              """));
    }
  }

  @Test
  void interimResultSweep_rejectsMissingAndRawUninitializedBooks() {
    Path missingBookPath = tempDirectory.resolve("interim-result-sweep-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath))) {
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          missingStore.interimResultSweep(
              emptyInterimResultSweepDraft(), () -> new PostingId("unused")));
      assertTrue(Files.notExists(missingBookPath));
    }

    Path blankBookPath = tempDirectory.resolve("interim-result-sweep-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore blankStore = openStore(bookAccess(blankBookPath))) {
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          blankStore.interimResultSweep(
              emptyInterimResultSweepDraft(), () -> new PostingId("unused")));
    }
  }

  @Test
  void interimResultSweep_highLevelRejectsMissingAndRawUninitializedBooksAtTheStoreBoundary() {
    Path missingBookPath = tempDirectory.resolve("interim-result-sweep-high-level-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath))) {
      InterimResultSweepService service =
          interimResultSweepService(
              fixedInitializedReader(),
              (reportingPeriod,
                  bookIdentity,
                  planner,
                  currentUtcDate,
                  sweptAt,
                  postingIdGenerator) ->
                  missingStore.interimResultSweep(
                      reportingPeriod,
                      bookIdentity,
                      planner,
                      currentUtcDate,
                      sweptAt,
                      postingIdGenerator),
              () -> new PostingId("unused"),
              FIXED_INSTANT);
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          service.interimResultSweep(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
      assertTrue(Files.notExists(missingBookPath));
    }

    Path blankBookPath = tempDirectory.resolve("interim-result-sweep-high-level-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore blankStore = openStore(bookAccess(blankBookPath))) {
      InterimResultSweepService service =
          interimResultSweepService(
              fixedInitializedReader(),
              (reportingPeriod,
                  bookIdentity,
                  planner,
                  currentUtcDate,
                  sweptAt,
                  postingIdGenerator) ->
                  blankStore.interimResultSweep(
                      reportingPeriod,
                      bookIdentity,
                      planner,
                      currentUtcDate,
                      sweptAt,
                      postingIdGenerator),
              () -> new PostingId("unused"),
              FIXED_INSTANT);
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          service.interimResultSweep(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
    }
  }

  @Test
  void interimResultSweep_rollsBackRejectedGeneratedPostingsBeforeAnyCloseFactIsStored() {
    Path databasePath = tempDirectory.resolve("interim-result-sweep-generated-rejection.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.interimResultSweep(
                      new InterimResultSweepDraft(
                          new ReportingPeriod(PERIOD_DATE, PERIOD_DATE),
                          new AccountCode("3200"),
                          List.of(),
                          FIXED_INSTANT,
                          List.of(
                              postingDraft(
                                  new JournalEntry(
                                      PERIOD_DATE,
                                      List.of(
                                          line("9999", JournalLine.EntrySide.DEBIT, "10.00"),
                                          line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
                                  dev.erst.fingrind.executor.bookkeeping.PostingLineageModel
                                      .direct(),
                                  PostingKind.STANDARD,
                                  dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                                  generatedEvidence(
                                      "generated-close-idem", "interim-result-sweep-plan"),
                                  postingFact(
                                          "generated-close-posting",
                                          "generated-close-idem",
                                          PERIOD_DATE,
                                          FIXED_INSTANT,
                                          List.of(
                                              line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                                              line("2000", JournalLine.EntrySide.CREDIT, "10.00")))
                                      .provenance()))),
                      () -> new PostingId("generated-close-posting")));
      assertTrue(
          NullTestSupport.messageOf(failure)
              .contains("Generated interim result sweep posting failed bookkeeping acceptance"),
          () -> NullTestSupport.messageOf(failure));
      assertEquals(
          0, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore), "select count(*) from interim_result_sweep"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where event_kind = 'INTERIM_RESULT_SWEPT'"));
    }
  }

  @Test
  void interimResultSweep_wrapsNativeFailuresFromStaleDatabaseHandles() throws Exception {
    Path databasePath = tempDirectory.resolve("interim-result-sweep-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore.interimResultSweep(
                        emptyInterimResultSweepDraft(), () -> new PostingId("unused")));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to close one SQLite reporting period."));
      }
    }
  }

  @Test
  void interimResultSweep_highLevelWrapsNativeFailuresFromStaleDatabaseHandles() throws Exception {
    Path databasePath = tempDirectory.resolve("interim-result-sweep-high-level-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath))) {
        InterimResultSweepService service =
            interimResultSweepService(
                fixedInitializedReader(),
                (reportingPeriod,
                    bookIdentity,
                    planner,
                    currentUtcDate,
                    sweptAt,
                    postingIdGenerator) ->
                    postingFactStore.interimResultSweep(
                        reportingPeriod,
                        bookIdentity,
                        planner,
                        currentUtcDate,
                        sweptAt,
                        postingIdGenerator),
                () -> new PostingId("unused"),
                FIXED_INSTANT);
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> service.interimResultSweep(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to close one SQLite reporting period."));
      }
    }
  }

  @Test
  void interimResultSweep_highLevelRollsBackRuntimeFailuresAfterTheTransactionBegins() {
    Path databasePath = tempDirectory.resolve("interim-result-sweep-high-level-runtime.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      AtomicReference<SqliteNativeDatabase> realDatabase =
          new AtomicReference<>(requireStoreDatabase(postingFactStore));
      setStoreDatabase(
          postingFactStore,
          new SqliteStatementRedirectingDatabase(
              realDatabase.get(),
              sql -> {
                if (SqlitePostingSql.LOAD_ALL_ACCOUNTS.equals(sql)) {
                  throw new IllegalStateException("forced high-level account-load failure");
                }
                return realDatabase.get().prepare(sql);
              }));

      try {
        InterimResultSweepService service =
            interimResultSweepService(
                fixedInitializedReader(),
                (reportingPeriod,
                    bookIdentity,
                    planner,
                    currentUtcDate,
                    sweptAt,
                    postingIdGenerator) ->
                    postingFactStore.interimResultSweep(
                        reportingPeriod,
                        bookIdentity,
                        planner,
                        currentUtcDate,
                        sweptAt,
                        postingIdGenerator),
                () -> new PostingId("unused"),
                FIXED_INSTANT);
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> service.interimResultSweep(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
        assertEquals("forced high-level account-load failure", failure.getMessage());
        assertEquals(0, queryInt(realDatabase.get(), "select count(*) from interim_result_sweep"));
        assertEquals(
            0,
            queryInt(
                realDatabase.get(),
                "select count(*) from audit_event where event_kind = 'INTERIM_RESULT_SWEPT'"));
      } finally {
        setStoreDatabase(postingFactStore, realDatabase.get());
      }
    }
  }

  private static InterimResultSweepDraft emptyInterimResultSweepDraft() {
    return new InterimResultSweepDraft(
        new ReportingPeriod(PERIOD_DATE, PERIOD_DATE),
        new AccountCode("3200"),
        List.of(),
        FIXED_INSTANT,
        List.of());
  }

  private static BookLifecycleReader fixedInitializedReader() {
    BookLifecycleInspection.Initialized initialized =
        initializedLifecycleInspection(
            SqliteBookContract.APPLICATION_ID,
            SqliteBookContract.FORMAT_VERSION,
            SqliteBookContract.FORMAT_VERSION,
            Instant.parse("2026-04-07T10:15:30Z"));
    return () -> initialized;
  }

  private static InterimResultSweepService interimResultSweepService(
      BookLifecycleReader lifecycleReader,
      TransferInvocation transferInvocation,
      PostingIdGenerator postingIdGenerator,
      Instant sweptAt) {
    return new InterimResultSweepService(
        lifecycleReader,
        new ReportingPeriodCloseStore() {
          @Override
          public InterimResultSweepOutcome interimResultSweep(
              ReportingPeriod reportingPeriod,
              dev.erst.fingrind.core.BookIdentity bookIdentity,
              dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
              LocalDate currentUtcDate,
              Instant sweptAt,
              PostingIdGenerator postingIdGenerator) {
            return transferInvocation.transfer(
                reportingPeriod,
                bookIdentity,
                planner,
                currentUtcDate,
                sweptAt,
                postingIdGenerator);
          }

          @Override
          public dev.erst.fingrind.executor.bookkeeping.FiscalYearCloseOutcome fiscalYearClose(
              ReportingPeriod reportingPeriod,
              dev.erst.fingrind.core.BookIdentity bookIdentity,
              dev.erst.fingrind.executor.bookkeeping.FiscalYearClosePlanner planner,
              LocalDate currentUtcDate,
              Instant closedAt,
              PostingIdGenerator postingIdGenerator) {
            throw new UnsupportedOperationException(
                "This helper only exercises interim-result sweep scenarios.");
          }
        },
        postingIdGenerator,
        java.time.Clock.fixed(sweptAt, java.time.ZoneOffset.UTC));
  }

  /** Invokes one interim-result sweep operation against the current store under test. */
  @FunctionalInterface
  private interface TransferInvocation {
    InterimResultSweepOutcome transfer(
        ReportingPeriod reportingPeriod,
        dev.erst.fingrind.core.BookIdentity bookIdentity,
        dev.erst.fingrind.executor.bookkeeping.InterimResultSweepPlanner planner,
        LocalDate currentUtcDate,
        Instant sweptAt,
        PostingIdGenerator postingIdGenerator);
  }
}
