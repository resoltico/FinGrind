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
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseDraft;
import dev.erst.fingrind.executor.bookkeeping.PeriodCloseOutcome;
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
  void closePeriod_rejectsMissingAndRawUninitializedBooks() {
    Path missingBookPath = tempDirectory.resolve("close-period-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath))) {
      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          missingStore.closePeriod(emptyPeriodCloseDraft(), () -> new PostingId("unused")));
      assertTrue(Files.notExists(missingBookPath));
    }

    Path blankBookPath = tempDirectory.resolve("close-period-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore blankStore = openStore(bookAccess(blankBookPath))) {
      assertEquals(
          new PeriodCloseOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          blankStore.closePeriod(emptyPeriodCloseDraft(), () -> new PostingId("unused")));
    }
  }

  @Test
  void closePeriod_rollsBackRejectedGeneratedPostingsBeforeAnyCloseFactIsStored() {
    Path databasePath = tempDirectory.resolve("close-period-generated-rejection.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.closePeriod(
                      new PeriodCloseDraft(
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
                                  generatedEvidence("generated-close-idem", "period-close-plan"),
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
              .contains("Generated period-close posting failed bookkeeping acceptance"));
      assertEquals(
          0, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from posting_fact"));
      assertEquals(
          0, queryInt(requireStoreDatabase(postingFactStore), "select count(*) from period_close"));
      assertEquals(
          0,
          queryInt(
              requireStoreDatabase(postingFactStore),
              "select count(*) from audit_event where event_kind = 'PERIOD_CLOSED'"));
    }
  }

  @Test
  void closePeriod_wrapsNativeFailuresFromStaleDatabaseHandles() throws Exception {
    Path databasePath = tempDirectory.resolve("close-period-stale.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      try (StoreDatabaseSwap ignored =
          swapStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath))) {
        IllegalStateException failure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    postingFactStore.closePeriod(
                        emptyPeriodCloseDraft(), () -> new PostingId("unused")));
        assertTrue(
            NullTestSupport.messageOf(failure)
                .contains("Failed to close one SQLite reporting period."));
      }
    }
  }

  private static PeriodCloseDraft emptyPeriodCloseDraft() {
    return new PeriodCloseDraft(
        new ReportingPeriod(PERIOD_DATE, PERIOD_DATE),
        new AccountCode("3200"),
        List.of(),
        FIXED_INSTANT,
        List.of());
  }
}
