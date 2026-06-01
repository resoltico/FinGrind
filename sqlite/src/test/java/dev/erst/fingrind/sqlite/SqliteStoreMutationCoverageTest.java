package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountRole;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.PeriodResultTransferService;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodResultTransferOutcome;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import dev.erst.fingrind.executor.spi.BookLifecycleReader;
import dev.erst.fingrind.executor.spi.PeriodResultTransferStore;
import dev.erst.fingrind.executor.spi.PostingDraft;
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
      postingFactStore.openBook(FIXED_INSTANT, bookIdentity());
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
                        AccountRole.ORDINARY,
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
  void declareAccount_redeclaresActiveAccountsWithoutMarkingThemAsReactivated() {
    Path databasePath = tempDirectory.resolve("declare-active-redeclare.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity());
      declareAccount(
          postingFactStore,
          new AccountCode("1000"),
          new AccountName("Cash"),
          AccountType.ASSET,
          dev.erst.fingrind.core.NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  AccountType.ASSET,
                  dev.erst.fingrind.core.NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-08T10:15:30Z"))),
          declareAccount(
              postingFactStore,
              new AccountCode("1000"),
              new AccountName("Cash main"),
              AccountType.ASSET,
              dev.erst.fingrind.core.NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          "ACCOUNT_DECLARED",
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
  void transferPeriodResult_rejectsMissingAndRawUninitializedBooks() {
    Path missingBookPath = tempDirectory.resolve("transfer-period-result-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath))) {
      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          missingStore.transferPeriodResult(
              emptyPeriodResultTransferDraft(), () -> new PostingId("unused")));
      assertTrue(Files.notExists(missingBookPath));
    }

    Path blankBookPath = tempDirectory.resolve("transfer-period-result-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore blankStore = openStore(bookAccess(blankBookPath))) {
      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          blankStore.transferPeriodResult(
              emptyPeriodResultTransferDraft(), () -> new PostingId("unused")));
    }
  }

  @Test
  void transferPeriodResult_highLevelRejectsMissingAndRawUninitializedBooksAtTheStoreBoundary() {
    Path missingBookPath =
        tempDirectory.resolve("transfer-period-result-high-level-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath))) {
      PeriodResultTransferService service =
          periodResultTransferService(
              fixedInitializedReader(),
              (reportingPeriod,
                  bookIdentity,
                  planner,
                  currentUtcDate,
                  transferredAt,
                  postingIdGenerator) ->
                  missingStore.transferPeriodResult(
                      reportingPeriod,
                      bookIdentity,
                      planner,
                      currentUtcDate,
                      transferredAt,
                      postingIdGenerator),
              () -> new PostingId("unused"),
              FIXED_INSTANT);
      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          service.transferPeriodResult(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
      assertTrue(Files.notExists(missingBookPath));
    }

    Path blankBookPath = tempDirectory.resolve("transfer-period-result-high-level-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore blankStore = openStore(bookAccess(blankBookPath))) {
      PeriodResultTransferService service =
          periodResultTransferService(
              fixedInitializedReader(),
              (reportingPeriod,
                  bookIdentity,
                  planner,
                  currentUtcDate,
                  transferredAt,
                  postingIdGenerator) ->
                  blankStore.transferPeriodResult(
                      reportingPeriod,
                      bookIdentity,
                      planner,
                      currentUtcDate,
                      transferredAt,
                      postingIdGenerator),
              () -> new PostingId("unused"),
              FIXED_INSTANT);
      assertEquals(
          new PeriodResultTransferOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          service.transferPeriodResult(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
    }
  }

  @Test
  void transferPeriodResult_rollsBackRejectedGeneratedPostingsBeforeAnyCloseFactIsStored() {
    Path databasePath = tempDirectory.resolve("transfer-period-result-generated-rejection.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.transferPeriodResult(
                      new PeriodResultTransferDraft(
                          new ReportingPeriod(PERIOD_DATE, PERIOD_DATE),
                          new AccountCode("3200"),
                          List.of(),
                          FIXED_INSTANT,
                          List.of(
                              new PostingDraft(
                                  new JournalEntry(
                                      PERIOD_DATE,
                                      List.of(
                                          line("9999", JournalLine.EntrySide.DEBIT, "10.00"),
                                          line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
                                  dev.erst.fingrind.executor.bookkeeping.PostingLineageModel
                                      .direct(),
                                  PostingKind.STANDARD,
                                  dev.erst.fingrind.core.PostingOriginKind.CORRECTION_ADJUSTMENT,
                                  generatedEvidence(
                                      "generated-close-idem", "period-result-transfer-plan"),
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
              .contains("Generated period-result-transfer posting failed bookkeeping acceptance"));
      assertEquals(
          0, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from period_result_transfer"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where event_kind = 'PERIOD_RESULT_TRANSFERRED'"));
    }
  }

  @Test
  void transferPeriodResult_wrapsNativeFailuresFromStaleDatabaseHandles() throws Exception {
    Path databasePath = tempDirectory.resolve("transfer-period-result-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore.transferPeriodResult(
                        emptyPeriodResultTransferDraft(), () -> new PostingId("unused")));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to close one SQLite reporting period."));
      }
    }
  }

  @Test
  void transferPeriodResult_highLevelWrapsNativeFailuresFromStaleDatabaseHandles()
      throws Exception {
    Path databasePath = tempDirectory.resolve("transfer-period-result-high-level-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath))) {
        PeriodResultTransferService service =
            periodResultTransferService(
                fixedInitializedReader(),
                (reportingPeriod,
                    bookIdentity,
                    planner,
                    currentUtcDate,
                    transferredAt,
                    postingIdGenerator) ->
                    postingFactStore.transferPeriodResult(
                        reportingPeriod,
                        bookIdentity,
                        planner,
                        currentUtcDate,
                        transferredAt,
                        postingIdGenerator),
                () -> new PostingId("unused"),
                FIXED_INSTANT);
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> service.transferPeriodResult(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to close one SQLite reporting period."));
      }
    }
  }

  @Test
  void transferPeriodResult_highLevelRollsBackRuntimeFailuresAfterTheTransactionBegins() {
    Path databasePath = tempDirectory.resolve("transfer-period-result-high-level-runtime.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
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
        PeriodResultTransferService service =
            periodResultTransferService(
                fixedInitializedReader(),
                (reportingPeriod,
                    bookIdentity,
                    planner,
                    currentUtcDate,
                    transferredAt,
                    postingIdGenerator) ->
                    postingFactStore.transferPeriodResult(
                        reportingPeriod,
                        bookIdentity,
                        planner,
                        currentUtcDate,
                        transferredAt,
                        postingIdGenerator),
                () -> new PostingId("unused"),
                FIXED_INSTANT);
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () -> service.transferPeriodResult(new ReportingPeriod(PERIOD_DATE, PERIOD_DATE)));
        assertEquals("forced high-level account-load failure", failure.getMessage());
        assertEquals(
            0, queryInt(realDatabase.get(), "select count(*) from period_result_transfer"));
        assertEquals(
            0,
            queryInt(
                realDatabase.get(),
                "select count(*) from audit_event where event_kind = 'PERIOD_RESULT_TRANSFERRED'"));
      } finally {
        setStoreDatabase(postingFactStore, realDatabase.get());
      }
    }
  }

  private static PeriodResultTransferDraft emptyPeriodResultTransferDraft() {
    return new PeriodResultTransferDraft(
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

  private static PeriodResultTransferService periodResultTransferService(
      BookLifecycleReader lifecycleReader,
      PeriodResultTransferStore store,
      dev.erst.fingrind.executor.spi.PostingIdGenerator postingIdGenerator,
      Instant transferredAt) {
    return new PeriodResultTransferService(
        lifecycleReader,
        store,
        postingIdGenerator,
        java.time.Clock.fixed(transferredAt, java.time.ZoneOffset.UTC));
  }
}
