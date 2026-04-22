package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountLedgerEntry;
import dev.erst.fingrind.contract.AccountLedgerQuery;
import dev.erst.fingrind.contract.AccountLedgerReport;
import dev.erst.fingrind.contract.BookAccess;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookMigrationPolicy;
import dev.erst.fingrind.contract.ContractDecision;
import dev.erst.fingrind.contract.ContractErrors;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.EffectiveDateRange;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PeriodAccountActivityRow;
import dev.erst.fingrind.contract.PeriodCurrencySummary;
import dev.erst.fingrind.contract.PeriodSummaryQuery;
import dev.erst.fingrind.contract.PeriodSummaryReport;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.contract.PostingPageCursor;
import dev.erst.fingrind.contract.PostingRejection;
import dev.erst.fingrind.contract.TrialBalanceQuery;
import dev.erst.fingrind.contract.TrialBalanceReport;
import dev.erst.fingrind.contract.TrialBalanceRow;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.PostingCommitResult;
import dev.erst.fingrind.executor.PostingDraft;
import dev.erst.fingrind.executor.PostingValidation;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
@NullUnmarked
class SqlitePostingFactStoreTest {
  private static final String TEST_BOOK_KEY = "posting-fact-store-test-book-key";

  @TempDir Path tempDirectory;

  @Test
  void findByIdempotency_returnsEmptyWhenPostingIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          Optional.empty(),
          postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findByPostingId_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-posting.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(Optional.empty(), postingFactStore.findPosting(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findReversalFor_returnsEmptyWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void commit_returnsBookNotInitializedWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-commit.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          rejected(new PostingRejection.BookNotInitialized()),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void openBook_rejectsSQLiteFileThatAlreadyContainsSchema() {
    Path databasePath = tempDirectory.resolve("legacy.sqlite");
    createPostingFactOnlyBook(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Rejected(new BookAdministrationRejection.BookContainsSchema()),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }
  }

  @Test
  void seamAccessors_returnStoreAsEachNarrowSessionView() {
    Path databasePath = tempDirectory.resolve("seam-accessors.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertNotNull(postingFactStore.administrationSession());
      assertNotNull(postingFactStore.postingSession());
      assertNotNull(postingFactStore.readSession());
      assertNotSame(postingFactStore, postingFactStore.administrationSession());
      assertNotSame(postingFactStore, postingFactStore.postingSession());
      assertNotSame(postingFactStore, postingFactStore.readSession());
    }
  }

  @Test
  void openResolved_transfersStoreOwnershipToCallerAfterSuccessfulPriming() {
    Path databasePath = tempDirectory.resolve("open-resolved.sqlite");

    try (SqliteBookPassphrase bookPassphrase =
        SqliteBookPassphrase.fromCharacters("open resolved", TEST_BOOK_KEY.toCharArray())) {
      ContractDecision<SqlitePostingFactStore> decision =
          SqlitePostingFactStore.openResolved(
              databasePath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
      switch (decision) {
        case ContractDecision.Accepted<SqlitePostingFactStore>(
                SqlitePostingFactStore postingFactStore) -> {
          try (postingFactStore) {
            assertFalse(Files.exists(databasePath));
            assertFalse(postingFactStore.isInitialized());
          }
        }
        case ContractDecision.Rejected<SqlitePostingFactStore>(var failure) ->
            throw new AssertionError("Expected store open to succeed but was " + failure.code());
      }
    }
  }

  @Test
  void openResolved_rejectsWrongPassphraseWithoutLeakingStoreOwnership() throws Exception {
    Path databasePath = tempDirectory.resolve("open-resolved-wrong-passphrase.sqlite");
    initializeBookOnDisk(databasePath);

    try (SqliteBookPassphrase wrongPassphrase =
        SqliteBookPassphrase.fromCharacters(
            "open resolved wrong passphrase", "wrong-passphrase".toCharArray())) {
      ContractDecision<SqlitePostingFactStore> decision =
          SqlitePostingFactStore.openResolved(
              databasePath, wrongPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE);
      switch (decision) {
        case ContractDecision.Accepted<SqlitePostingFactStore>(
                SqlitePostingFactStore postingFactStore) -> {
          try (postingFactStore) {
            throw new AssertionError("Expected wrong passphrase to be rejected.");
          }
        }
        case ContractDecision.Rejected<SqlitePostingFactStore>(var failure) ->
            assertEquals(
                ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.code(), failure.code());
      }
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void administrationView_delegatesMutationsWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("administration-view.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      var administrationView = postingFactStore.administrationSession();
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          administrationView.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          administrationView.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void postingView_delegatesReadsWritesWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("posting-view.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      var postingView = postingFactStore.postingSession();
      assertTrue(postingView.isInitialized());
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          postingView.findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))),
          postingView.findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertEquals(
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          postingView.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          postingView.findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.findReversalFor(new PostingId("posting-1")),
          postingView.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())),
          postingView.commit(
              postingDraft("posting-2", "idem-2", Optional.empty(), Optional.empty()),
              () -> new PostingId("posting-2")));
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void queryView_delegatesQueriesWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("query-view.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      ListPostingsQuery postingsQuery =
          new ListPostingsQuery(
              Optional.empty(), Optional.empty(), Optional.empty(), 50, Optional.empty());
      AccountBalanceQuery balanceQuery =
          new AccountBalanceQuery(new AccountCode("1000"), Optional.empty(), Optional.empty());

      var queryView = postingFactStore.readSession();
      assertEquals(postingFactStore.inspectBook(), queryView.inspectBook());
      assertTrue(queryView.isInitialized());
      assertEquals(
          postingFactStore.listAccounts(firstAccountPage()),
          queryView.listAccounts(firstAccountPage()));
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          queryView.findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          queryView.findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.listPostings(postingsQuery), queryView.listPostings(postingsQuery));
      assertEquals(
          postingFactStore.accountBalance(balanceQuery), queryView.accountBalance(balanceQuery));
      assertEquals(postingFactStore.inspectBook(), queryView.inspectBook());
    }
  }

  @Test
  void reportView_delegatesReportsWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("report-view.sqlite");
    PostingFact openingPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact zeroingPosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "10.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(openingPosting);
      postingFactStore.commit(zeroingPosting);

      DeclaredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      var reportView = postingFactStore.readSession();
      assertTrue(reportView.isInitialized());
      assertEquals(Optional.of(cashAccount), reportView.findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())),
          reportView.trialBalance(new TrialBalanceQuery(Optional.empty())));
      assertEquals(
          new AccountLedgerReport(
              revenueAccount,
              EffectiveDateRange.unbounded(),
              List.of(),
              List.of(
                  new AccountLedgerEntry(
                      openingPosting,
                      new CurrencyBalance(
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          money("EUR", "10.00"),
                          BalanceSide.CREDIT),
                      money("EUR", "10.00"),
                      BalanceSide.CREDIT),
                  new AccountLedgerEntry(
                      zeroingPosting,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          BalanceSide.DEBIT),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO))),
          reportView.accountLedger(
              new AccountLedgerQuery(new AccountCode("2000"), EffectiveDateRange.unbounded()),
              revenueAccount));
      assertEquals(
          postingFactStore.periodSummary(
              new PeriodSummaryQuery(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))),
          reportView.periodSummary(
              new PeriodSummaryQuery(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))));
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void reportMethods_throwBookNotInitializedWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("missing-report-book.sqlite");
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())));
      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty()),
                      cashAccount));
      IllegalStateException periodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryQuery(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));

      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          trialBalanceFailure.getMessage());
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          accountLedgerFailure.getMessage());
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          periodSummaryFailure.getMessage());
    }
  }

  @Test
  void queryView_requiresInitializedBookForDirectQueryCalls() throws Exception {
    ListPostingsQuery postingsQuery =
        new ListPostingsQuery(
            Optional.empty(), Optional.empty(), Optional.empty(), 50, Optional.empty());
    AccountBalanceQuery balanceQuery =
        new AccountBalanceQuery(new AccountCode("1000"), Optional.empty(), Optional.empty());

    Path missingBookPath = tempDirectory.resolve("query-view-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      var queryView = postingFactStore.readSession();
      assertFalse(queryView.isInitialized());
      assertInitializedQueryViewFailure(
          () -> queryView.listAccounts(firstAccountPage()),
          () -> queryView.findAccount(new AccountCode("1000")),
          () -> queryView.findPosting(new PostingId("posting-1")),
          () -> queryView.listPostings(postingsQuery),
          () -> queryView.accountBalance(balanceQuery));
    }

    Path rawSqlitePath = tempDirectory.resolve("query-view-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(rawSqlitePath))) {
      var queryView = postingFactStore.readSession();
      assertFalse(queryView.isInitialized());
      assertInitializedQueryViewFailure(
          () -> queryView.listAccounts(firstAccountPage()),
          () -> queryView.findAccount(new AccountCode("1000")),
          () -> queryView.findPosting(new PostingId("posting-1")),
          () -> queryView.listPostings(postingsQuery),
          () -> queryView.accountBalance(balanceQuery));
    }
  }

  @Test
  void queryView_wrapsNativeFailuresFromDirectQueryCalls() throws Exception {
    Path databasePath = tempDirectory.resolve("query-view-native-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      var queryView = postingFactStore.readSession();
      setStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath));

      assertWrappedQueryViewNativeFailure(
          () -> queryView.listAccounts(firstAccountPage()),
          () -> queryView.findAccount(new AccountCode("1000")),
          () -> queryView.findPosting(new PostingId("posting-1")),
          () ->
              queryView.listPostings(
                  new ListPostingsQuery(
                      Optional.empty(), Optional.empty(), Optional.empty(), 50, Optional.empty())),
          () ->
              queryView.accountBalance(
                  new AccountBalanceQuery(
                      new AccountCode("1000"), Optional.empty(), Optional.empty())));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void ledgerPlanTransaction_commitsOuterTransactionAndPersistsNestedMutations() {
    Path databasePath = tempDirectory.resolve("ledger-plan-commit.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();

      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:31Z"))),
          postingFactStore.declareAccount(
              new AccountCode("2000"),
              new AccountName("Revenue"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-07T10:15:31Z")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())),
          postingFactStore.commit(
              postingDraft("posting-1", "idem-1", Optional.empty(), Optional.empty()),
              () -> new PostingId("posting-1")));

      postingFactStore.commitLedgerPlanTransaction();
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertTrue(postingFactStore.isInitialized());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isPresent());
      assertTrue(postingFactStore.findPosting(new PostingId("posting-1")).isPresent());
    }
  }

  @Test
  void ledgerPlanTransaction_rollsBackOuterTransactionAndRejectsInvalidLifecycleCalls() {
    Path databasePath = tempDirectory.resolve("ledger-plan-rollback.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertThrows(IllegalStateException.class, postingFactStore::commitLedgerPlanTransaction);

      postingFactStore.beginLedgerPlanTransaction();
      assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);

      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.rollbackLedgerPlanTransaction();
      postingFactStore.rollbackLedgerPlanTransaction();
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertFalse(postingFactStore.isInitialized());
      assertTrue(postingFactStore.findAccount(new AccountCode("1000")).isEmpty());
    }
  }

  @Test
  void ledgerPlanTransaction_defersExistingHandleValidationUntilDatabaseWork() throws Exception {
    Path beginFailurePath = tempDirectory.resolve("ledger-plan-deferred-begin.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(beginFailurePath))) {
      try (SqliteNativeDatabase closedDatabase =
          SqliteNativeLibrary.open(bookAccess(beginFailurePath))) {
        closedDatabase.close();
        setStoreDatabase(postingFactStore, closedDatabase);
      }

      assertDoesNotThrow(postingFactStore::beginLedgerPlanTransaction);
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }

    Path commitFailurePath = tempDirectory.resolve("ledger-plan-commit-failure.sqlite");
    initializeBookOnDisk(commitFailurePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(commitFailurePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      closeStoreDatabase(postingFactStore);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::commitLedgerPlanTransaction);

      assertTrue(
          exception.getMessage().contains("Failed to commit SQLite ledger plan transaction."));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }

  @Test
  void findAccounts_returnsEmptyForMissingAndBlankBooksAndDeclaredRowsForInitializedBooks() {
    AccountCode cash = new AccountCode("1000");
    AccountCode revenue = new AccountCode("2000");
    Set<AccountCode> requestedAccounts = Set.of(cash, revenue);

    Path missingPath = tempDirectory.resolve("find-accounts-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingPath))) {
      assertEquals(Map.of(), postingFactStore.findAccounts(Set.of()));
      assertEquals(Map.of(), postingFactStore.findAccounts(requestedAccounts));
    }

    Path blankPath = tempDirectory.resolve("find-accounts-blank.sqlite");
    createEmptySqliteFile(blankPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankPath))) {
      assertEquals(Map.of(), postingFactStore.findAccounts(requestedAccounts));
    }

    Path initializedPath = tempDirectory.resolve("find-accounts-initialized.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          Map.of(
              cash,
              postingFactStore.findAccount(cash).orElseThrow(),
              revenue,
              postingFactStore.findAccount(revenue).orElseThrow()),
          postingFactStore.findAccounts(requestedAccounts));
    }
  }

  @Test
  void ledgerPlanTransaction_preservesMissingBookStateUntilPlanMutation() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-missing.sqlite");

    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "plan execution missing book", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertNull(storeDatabase(postingFactStore));
      postingFactStore.commitLedgerPlanTransaction();
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void ledgerPlanTransaction_opensExistingBookImmediatelyInPlanExecutionMode() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-existing.sqlite");
    initializeBookOnDisk(databasePath);

    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "plan execution existing book", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.PLAN_EXECUTION)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertNotNull(storeDatabase(postingFactStore));
      postingFactStore.rollbackLedgerPlanTransaction();
    }
  }

  @Test
  void ledgerPlanTransaction_resetsActivationFlagsWhenBeginCannotOpenDatabase() throws Exception {
    Path databasePath = tempDirectory.resolve("ledger-plan-begin-passphrase-missing.sqlite");
    initializeBookOnDisk(databasePath);

    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "begin failure missing passphrase", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                databasePath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      setStoreBookPassphrase(postingFactStore, null);

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::beginLedgerPlanTransaction);

      assertEquals("SQLite book passphrase is no longer available.", exception.getMessage());
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
    }
  }

  @Test
  void ledgerPlanTransaction_rollbackToleratesClosedStoreWithActiveOuterTransaction() {
    Path databasePath = tempDirectory.resolve("ledger-plan-closed-rollback.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.beginLedgerPlanTransaction();
      postingFactStore.close();

      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }

  @Test
  void close_retainsDatabaseHandleUntilCloseEventuallySucceeds() throws Exception {
    Path databasePath = tempDirectory.resolve("close-retry.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      MemorySegment activeHandle = requireStoreDatabase(postingFactStore).handle();

      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3CloseV2HandleForTesting(
              constantMethodHandle(14, MemorySegment.class))) {
        IllegalStateException exception =
            assertThrows(IllegalStateException.class, postingFactStore::close);

        assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
        assertSame(activeHandle, requireStoreDatabase(postingFactStore).handle());
        assertFalse(storeBooleanField(postingFactStore, "closed"));
      }

      assertDoesNotThrow(postingFactStore::close);
      assertNull(storeDatabase(postingFactStore));
      assertTrue(storeBooleanField(postingFactStore, "closed"));
    }
  }

  @Test
  void closeReopenedDatabaseQuietly_toleratesNullAndNativeCloseFailures() {
    try (ClosingSqliteNativeDatabase closingDatabase = new ClosingSqliteNativeDatabase();
        ThrowingSqliteNativeDatabase database = new ThrowingSqliteNativeDatabase()) {
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(closingDatabase));
      assertTrue(closingDatabase.closeAttempted());
      assertDoesNotThrow(
          () ->
              SqliteStoreOperations.closeReopenedDatabaseQuietly(
                  new SqliteSessionDatabase(closingDatabase)));
      assertDoesNotThrow(() -> SqliteStoreOperations.closeReopenedDatabaseQuietly(database));
      assertTrue(database.closeAttempted());
      assertDoesNotThrow(
          () -> SqliteStoreOperations.closeReopenedDatabaseQuietly((SqliteNativeDatabase) null));
    }
  }

  @Test
  void declareAccount_requiresInitializedBook() {
    Path databasePath = tempDirectory.resolve("declare-uninitialized.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void storeOperations_handleMissingAndRawUninitializedSqliteBooks() {
    Path missingBookPath = tempDirectory.resolve("missing-ops.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(List.of(), listAccounts(postingFactStore));
      assertEquals(
          Optional.empty(), postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new PostingRejection.BookNotInitialized()),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertFalse(Files.exists(missingBookPath));
    }

    Path rawSqlitePath = tempDirectory.resolve("raw-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(rawSqlitePath))) {
      assertFalse(postingFactStore.isInitialized());
      assertEquals(Optional.empty(), postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(List.of(), listAccounts(postingFactStore));
      assertEquals(
          Optional.empty(), postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(Optional.empty(), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          rejected(new PostingRejection.BookNotInitialized()),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void storeOperations_wrapFailuresForInvalidBookFiles() throws IOException {
    Path invalidBookPath = tempDirectory.resolve("not-a-sqlite-file.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.declareAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));

      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void openBook_rejectsAlreadyInitializedBook() {
    Path databasePath = tempDirectory.resolve("already-initialized.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
          postingFactStore.openBook(Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void openBook_initializesBlankSqliteFile() {
    Path databasePath = tempDirectory.resolve("blank-before-open.sqlite");
    createEmptySqliteFile(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }
  }

  @Test
  void schemaOnlyBook_isRejectedAsIncompleteFinGrindBook() {
    Path databasePath = tempDirectory.resolve("schema-only.sqlite");
    createSchemaOnlyBook(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertTrue(
          initializedException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(
          accountException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));

      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(
          openException
              .getMessage()
              .contains("incomplete or corrupted and cannot be opened safely"));
    }
  }

  @Test
  void openBook_setsFinGrindIdentityAndHardeningPragmas() throws Exception {
    Path databasePath = tempDirectory.resolve("identity-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma secure_delete"));
      assertEquals(2, queryInt(storeDatabase(postingFactStore), "pragma temp_store"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              SqliteBookContract.APPLICATION_ID, queryInt(database, "pragma application_id"));
          assertEquals(
              SqliteBookContract.FORMAT_VERSION, queryInt(database, "pragma user_version"));
        });
  }

  @Test
  void foreignAndUnsupportedBooks_areRejectedAcrossBoundaries() throws Exception {
    Path foreignBookPath = tempDirectory.resolve("foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", initializedException.getMessage());

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertEquals(
          "The selected SQLite file is not a FinGrind book.", accountException.getMessage());
    }

    Path unsupportedBookPath = tempDirectory.resolve("unsupported-version.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      IllegalStateException initializedException =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      assertTrue(initializedException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException openException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(openException.getMessage().contains("format version 2 is unsupported"));

      IllegalStateException accountException =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));
      assertTrue(accountException.getMessage().contains("format version 2 is unsupported"));
    }
  }

  @Test
  void declareAccount_listsAndReactivatesAccounts() {
    Path databasePath = tempDirectory.resolve("declare-accounts.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));

      deactivateAccount(databasePath, "1000");

      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash main"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T10:15:30Z")));
      assertEquals(
          List.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          listAccounts(postingFactStore));
    }
  }

  @Test
  void findAccount_returnsDeclaredAccountFromInitializedBook() {
    Path databasePath = tempDirectory.resolve("find-account.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      assertEquals(
          Optional.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void declareAccount_rejectsNormalBalanceConflict() {
    Path databasePath = tempDirectory.resolve("declare-conflict.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          new DeclareAccountResult.Rejected(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          postingFactStore.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-08T10:15:30Z")));
    }
  }

  @Test
  void listAccounts_paginatesDeclaredRegistry() {
    Path databasePath = tempDirectory.resolve("list-accounts-paginated.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("1000"),
          new AccountName("Cash"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("2000"),
          new AccountName("Revenue"),
          NormalBalance.CREDIT,
          Instant.parse("2026-04-07T10:15:30Z"));
      postingFactStore.declareAccount(
          new AccountCode("3000"),
          new AccountName("Receivable"),
          NormalBalance.DEBIT,
          Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          List.of(new AccountCode("1000"), new AccountCode("2000")),
          postingFactStore.listAccounts(new ListAccountsQuery(2, 0)).accounts().stream()
              .map(DeclaredAccount::accountCode)
              .toList());
      assertTrue(postingFactStore.listAccounts(new ListAccountsQuery(2, 0)).hasMore());
      assertEquals(
          List.of(new AccountCode("3000")),
          postingFactStore.listAccounts(new ListAccountsQuery(2, 2)).accounts().stream()
              .map(DeclaredAccount::accountCode)
              .toList());
      assertFalse(postingFactStore.listAccounts(new ListAccountsQuery(2, 2)).hasMore());
    }
  }

  @Test
  void inspectBook_reportsLifecycleAndCompatibilityStates() throws Exception {
    Path missingBookPath = tempDirectory.resolve("inspect-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertEquals(
          new BookInspection.Missing(
              SqliteBookContract.FORMAT_VERSION, BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path blankBookPath = tempDirectory.resolve("inspect-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.BLANK_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path initializedBookPath = tempDirectory.resolve("inspect-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(initializedBookPath))) {
      assertEquals(
          new BookInspection.Initialized(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE,
              Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.inspectBook());
    }

    Path foreignBookPath = tempDirectory.resolve("inspect-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.FOREIGN_SQLITE,
              0,
              0,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path unsupportedBookPath = tempDirectory.resolve("inspect-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(unsupportedBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.UNSUPPORTED_FORMAT_VERSION,
              SqliteBookContract.APPLICATION_ID,
              2,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }

    Path incompleteBookPath = tempDirectory.resolve("inspect-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(incompleteBookPath))) {
      assertEquals(
          new BookInspection.Existing(
              BookInspection.Status.INCOMPLETE_FINGRIND,
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookContract.FORMAT_VERSION,
              BookMigrationPolicy.SEQUENTIAL_IN_PLACE),
          postingFactStore.inspectBook());
    }
  }

  @Test
  void listPostings_returnsEmptyPagesForMissingAndBlankBooks() throws Exception {
    ListPostingsQuery firstPage =
        new ListPostingsQuery(
            Optional.empty(), Optional.empty(), Optional.empty(), 2, Optional.empty());

    Path missingBookPath = tempDirectory.resolve("list-postings-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertEquals(
          new PostingPage(List.of(), 2, Optional.empty()),
          postingFactStore.listPostings(firstPage));
    }

    Path blankBookPath = tempDirectory.resolve("list-postings-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      assertEquals(
          new PostingPage(List.of(), 2, Optional.empty()),
          postingFactStore.listPostings(firstPage));
    }
  }

  @Test
  void listPostings_filtersAndPaginatesCommittedPostings() {
    Path databasePath = tempDirectory.resolve("list-postings.sqlite");
    PostingFact postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("3000", JournalLine.EntrySide.DEBIT, "EUR", "20.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "20.00")));
    PostingFact postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "30.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "30.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("3000"),
                  new AccountName("Receivable"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("3000"),
              new AccountName("Receivable"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new PostingCommitResult.Committed(postingOne), postingFactStore.commit(postingOne));
      assertEquals(
          new PostingCommitResult.Committed(postingTwo), postingFactStore.commit(postingTwo));
      assertEquals(
          new PostingCommitResult.Committed(postingThree), postingFactStore.commit(postingThree));

      assertEquals(
          new PostingPage(
              List.of(postingThree, postingTwo),
              2,
              Optional.of(PostingPageCursor.fromPosting(postingTwo))),
          postingFactStore.listPostings(
              new ListPostingsQuery(
                  Optional.empty(), Optional.empty(), Optional.empty(), 2, Optional.empty())));
      assertEquals(
          new PostingPage(List.of(postingOne), 2, Optional.empty()),
          postingFactStore.listPostings(
              new ListPostingsQuery(
                  Optional.empty(),
                  Optional.empty(),
                  Optional.empty(),
                  2,
                  Optional.of(PostingPageCursor.fromPosting(postingTwo)))));
      assertEquals(
          new PostingPage(List.of(postingOne), 50, Optional.empty()),
          postingFactStore.listPostings(
              new ListPostingsQuery(
                  Optional.of(new AccountCode("1000")),
                  Optional.of(LocalDate.parse("2026-04-07")),
                  Optional.of(LocalDate.parse("2026-04-08")),
                  50,
                  Optional.empty())));
    }
  }

  @Test
  void accountBalance_validatesBookStateAndComputesCurrencyBuckets() throws Exception {
    Path missingBookPath = tempDirectory.resolve("account-balance-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty())));

      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }

    Path blankBookPath = tempDirectory.resolve("account-balance-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty())));

      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }

    Path databasePath = tempDirectory.resolve("account-balance.sqlite");
    PostingFact postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    PostingFact postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "8.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "8.00")));
    PostingFact postingFour =
        postingFact(
            "posting-4",
            "idem-4",
            LocalDate.parse("2026-04-10"),
            Instant.parse("2026-04-10T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "7.00")));
    PostingFact postingFive =
        postingFact(
            "posting-5",
            "idem-5",
            LocalDate.parse("2026-04-11"),
            Instant.parse("2026-04-11T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "USD", "7.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingOne);
      postingFactStore.commit(postingTwo);
      postingFactStore.commit(postingThree);
      postingFactStore.commit(postingFour);
      postingFactStore.commit(postingFive);

      assertEquals(
          Optional.empty(),
          postingFactStore.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("9999"), Optional.empty(), Optional.empty())));

      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      Instant.parse("2026-04-07T10:15:30Z")),
                  Optional.empty(),
                  Optional.empty(),
                  List.of(
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "12.00"),
                          money("EUR", "2.00"),
                          BalanceSide.CREDIT),
                      new CurrencyBalance(
                          money("USD", "7.00"),
                          money("USD", "7.00"),
                          money("USD", "0.00"),
                          BalanceSide.ZERO)))),
          postingFactStore.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"), Optional.empty(), Optional.empty())));
      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      Instant.parse("2026-04-07T10:15:30Z")),
                  Optional.empty(),
                  Optional.of(LocalDate.parse("2026-04-08")),
                  List.of(
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)))),
          postingFactStore.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"),
                  Optional.empty(),
                  Optional.of(LocalDate.parse("2026-04-08")))));
      assertEquals(
          Optional.of(
              new AccountBalanceSnapshot(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      Instant.parse("2026-04-07T10:15:30Z")),
                  Optional.of(LocalDate.parse("2026-04-10")),
                  Optional.of(LocalDate.parse("2026-04-11")),
                  List.of(
                      new CurrencyBalance(
                          money("USD", "7.00"),
                          money("USD", "7.00"),
                          money("USD", "0.00"),
                          BalanceSide.ZERO)))),
          postingFactStore.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"),
                  Optional.of(LocalDate.parse("2026-04-10")),
                  Optional.of(LocalDate.parse("2026-04-11")))));
    }
  }

  @Test
  void trialBalance_andPeriodSummary_computeOfficeReportModels() {
    Path databasePath = tempDirectory.resolve("office-reports.sqlite");
    PostingFact postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    PostingFact postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "8.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "8.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingOne);
      postingFactStore.commit(postingTwo);
      postingFactStore.commit(postingThree);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      DeclaredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();

      assertEquals(
          new TrialBalanceReport(
              Optional.of(LocalDate.parse("2026-04-08")),
              List.of(
                  new TrialBalanceRow(
                      cashAccount,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)),
                  new TrialBalanceRow(
                      revenueAccount,
                      new CurrencyBalance(
                          money("EUR", "4.00"),
                          money("EUR", "10.00"),
                          money("EUR", "6.00"),
                          BalanceSide.CREDIT)))),
          postingFactStore.trialBalance(
              new TrialBalanceQuery(Optional.of(LocalDate.parse("2026-04-08")))));

      assertEquals(
          new PeriodSummaryReport(
              LocalDate.parse("2026-04-07"),
              LocalDate.parse("2026-04-08"),
              2,
              4,
              2,
              List.of(
                  new PeriodCurrencySummary(
                      new CurrencyBalance(
                          money("EUR", "14.00"),
                          money("EUR", "14.00"),
                          money("EUR", "0.00"),
                          BalanceSide.ZERO))),
              List.of(
                  new PeriodAccountActivityRow(
                      cashAccount,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "4.00"),
                          money("EUR", "6.00"),
                          BalanceSide.DEBIT)),
                  new PeriodAccountActivityRow(
                      revenueAccount,
                      new CurrencyBalance(
                          money("EUR", "4.00"),
                          money("EUR", "10.00"),
                          money("EUR", "6.00"),
                          BalanceSide.CREDIT)))),
          postingFactStore.periodSummary(
              new PeriodSummaryQuery(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))));
    }
  }

  @Test
  void accountLedger_computesOpeningRunningAndClosingBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-report.sqlite");
    PostingFact postingOne =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact postingTwo =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "4.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "4.00")));
    PostingFact postingThree =
        postingFact(
            "posting-3",
            "idem-3",
            LocalDate.parse("2026-04-09"),
            Instant.parse("2026-04-09T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "8.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "8.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingOne);
      postingFactStore.commit(postingTwo);
      postingFactStore.commit(postingThree);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(
                  Optional.of(LocalDate.parse("2026-04-08")),
                  Optional.of(LocalDate.parse("2026-04-09"))),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new AccountLedgerEntry(
                      postingTwo,
                      new CurrencyBalance(
                          money("EUR", "0.00"),
                          money("EUR", "4.00"),
                          money("EUR", "4.00"),
                          BalanceSide.CREDIT),
                      money("EUR", "6.00"),
                      BalanceSide.DEBIT),
                  new AccountLedgerEntry(
                      postingThree,
                      new CurrencyBalance(
                          money("USD", "8.00"),
                          money("USD", "0.00"),
                          money("USD", "8.00"),
                          BalanceSide.DEBIT),
                      money("USD", "8.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "4.00"),
                      money("EUR", "6.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "8.00"),
                      money("USD", "0.00"),
                      money("USD", "8.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("1000"),
                  Optional.of(LocalDate.parse("2026-04-08")),
                  Optional.of(LocalDate.parse("2026-04-09"))),
              cashAccount));
    }
  }

  @Test
  void accountLedger_allowsMinimumLowerBoundWithoutOpeningBalanceLookback() {
    Path databasePath = tempDirectory.resolve("account-ledger-min-lower-bound.sqlite");
    PostingFact posting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(posting);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(Optional.of(LocalDate.MIN), Optional.empty()),
              List.of(),
              List.of(
                  new AccountLedgerEntry(
                      posting,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          BalanceSide.DEBIT),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("1000"), Optional.of(LocalDate.MIN), Optional.empty()),
              cashAccount));
    }
  }

  @Test
  void accountLedger_supportsCreditOpeningBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-credit-opening.sqlite");
    PostingFact openingPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact inRangePosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.CREDIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.DEBIT, "EUR", "10.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(openingPosting);
      postingFactStore.commit(inRangePosting);

      DeclaredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              revenueAccount,
              EffectiveDateRange.of(
                  Optional.of(LocalDate.parse("2026-04-08")),
                  Optional.of(LocalDate.parse("2026-04-08"))),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      money("EUR", "10.00"),
                      BalanceSide.CREDIT)),
              List.of(
                  new AccountLedgerEntry(
                      inRangePosting,
                      new CurrencyBalance(
                          money("EUR", "10.00"),
                          money("EUR", "0.00"),
                          money("EUR", "10.00"),
                          BalanceSide.DEBIT),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO)),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("2000"),
                  Optional.of(LocalDate.parse("2026-04-08")),
                  Optional.of(LocalDate.parse("2026-04-08"))),
              revenueAccount));
    }
  }

  @Test
  void accountLedger_sortsMultipleOpeningCurrenciesBeforeRunningBalances() {
    Path databasePath = tempDirectory.resolve("account-ledger-multi-opening.sqlite");
    PostingFact eurOpeningPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    PostingFact usdOpeningPosting =
        postingFact(
            "posting-2",
            "idem-2",
            LocalDate.parse("2026-04-08"),
            Instant.parse("2026-04-08T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "USD", "7.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "USD", "7.00")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(eurOpeningPosting);
      postingFactStore.commit(usdOpeningPosting);

      DeclaredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();

      assertEquals(
          new AccountLedgerReport(
              cashAccount,
              EffectiveDateRange.of(
                  Optional.of(LocalDate.parse("2026-04-09")),
                  Optional.of(LocalDate.parse("2026-04-09"))),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "7.00"),
                      money("USD", "0.00"),
                      money("USD", "7.00"),
                      BalanceSide.DEBIT)),
              List.of(),
              List.of(
                  new CurrencyBalance(
                      money("EUR", "10.00"),
                      money("EUR", "0.00"),
                      money("EUR", "10.00"),
                      BalanceSide.DEBIT),
                  new CurrencyBalance(
                      money("USD", "7.00"),
                      money("USD", "0.00"),
                      money("USD", "7.00"),
                      BalanceSide.DEBIT))),
          postingFactStore.accountLedger(
              new AccountLedgerQuery(
                  new AccountCode("1000"),
                  Optional.of(LocalDate.parse("2026-04-09")),
                  Optional.of(LocalDate.parse("2026-04-09"))),
              cashAccount));
    }
  }

  @Test
  void queryMethods_wrapFailuresForInvalidBookFiles() throws IOException {
    Path invalidBookPath = tempDirectory.resolve("query-not-a-sqlite-file.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);
    DeclaredAccount cashAccount =
        new DeclaredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new ListPostingsQuery(
                          Optional.empty(),
                          Optional.empty(),
                          Optional.empty(),
                          10,
                          Optional.empty())));
      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty())));
      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())));
      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty()),
                      cashAccount));
      assertInvalidPlaintextBookFailure(exception);
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryQuery(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void queryMethods_wrapNativeFailuresAfterDatabaseOpen() throws Exception {
    Path bookPath = tempDirectory.resolve("query-stale.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      DeclaredAccount cashAccount =
          new DeclaredAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              true,
              Instant.parse("2026-04-07T10:15:30Z"));

      IllegalStateException inspectFailure =
          assertThrows(IllegalStateException.class, postingFactStore::inspectBook);
      assertTrue(inspectFailure.getMessage().contains("Failed to inspect SQLite book."));

      IllegalStateException listFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.listPostings(
                      new ListPostingsQuery(
                          Optional.empty(),
                          Optional.empty(),
                          Optional.empty(),
                          10,
                          Optional.empty())));
      assertTrue(listFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException balanceFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountBalance(
                      new AccountBalanceQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty())));
      assertTrue(balanceFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceQuery(Optional.empty())));
      assertTrue(trialBalanceFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerQuery(
                          new AccountCode("1000"), Optional.empty(), Optional.empty()),
                      cashAccount));
      assertTrue(accountLedgerFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException periodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryQuery(
                          LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
      assertTrue(periodSummaryFailure.getMessage().contains("Failed to query SQLite book."));

      var readView = postingFactStore.readSession();
      {
        IllegalStateException readTrialBalanceFailure =
            assertThrows(
                IllegalStateException.class,
                () -> readView.trialBalance(new TrialBalanceQuery(Optional.empty())));
        assertTrue(readTrialBalanceFailure.getMessage().contains("Failed to query SQLite book."));

        IllegalStateException readAccountLedgerFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    readView.accountLedger(
                        new AccountLedgerQuery(
                            new AccountCode("1000"), Optional.empty(), Optional.empty()),
                        cashAccount));
        assertTrue(readAccountLedgerFailure.getMessage().contains("Failed to query SQLite book."));

        IllegalStateException readPeriodSummaryFailure =
            assertThrows(
                IllegalStateException.class,
                () ->
                    readView.periodSummary(
                        new PeriodSummaryQuery(
                            LocalDate.parse("2026-04-01"), LocalDate.parse("2026-04-30"))));
        assertTrue(readPeriodSummaryFailure.getMessage().contains("Failed to query SQLite book."));
        setStoreDatabase(postingFactStore, null);
      }
    }
  }

  @Test
  void commit_returnsUnknownAndInactiveAccountOutcomes() {
    Path databasePath = tempDirectory.resolve("account-rejections.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(
          rejected(
              accountStateViolations(
                  new PostingRejection.UnknownAccount(new AccountCode("1000")),
                  new PostingRejection.UnknownAccount(new AccountCode("2000")))),
          postingFactStore.commit(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      declareDefaultAccounts(postingFactStore);
      deactivateAccount(databasePath, "1000");

      assertEquals(
          rejected(
              accountStateViolations(
                  new PostingRejection.InactiveAccount(new AccountCode("1000")))),
          postingFactStore.commit(
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commitAndFinders_roundTripPostingWithoutReversal() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(postingFact), postingFactStore.commit(postingFact));
      assertEquals(
          Optional.of(postingFact),
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          Optional.of(postingFact), postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(Optional.empty(), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void openBook_initializesCanonicalTablesAsStrict() {
    Path databasePath = tempDirectory.resolve("strict-schema.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.isInitialized());
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database -> {
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('book_meta') where name = 'book_meta'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('account') where name = 'account'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('posting_fact') where name = 'posting_fact'"));
          assertEquals(
              1,
              queryInt(
                  database,
                  "select strict from pragma_table_list('journal_line') where name = 'journal_line'"));
        });
  }

  @Test
  void openBook_createsAccountCodeIndexForJournalLines() {
    Path databasePath = tempDirectory.resolve("journal-line-index.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database ->
            assertEquals(
                1,
                queryInt(
                    database,
                    """
                    select count(*)
                    from pragma_index_list('journal_line')
                    where name = 'journal_line_by_account_code'
                    """)));
  }

  @Test
  void openBook_createsPostingHistoryIndexForReverseChronologicalPages() {
    Path databasePath = tempDirectory.resolve("posting-history-index.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new OpenBookResult.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }

    withStandaloneDatabase(
        bookAccess(databasePath),
        database ->
            assertEquals(
                1,
                queryInt(
                    database,
                    """
                    select count(*)
                    from pragma_index_list('posting_fact')
                    where name = 'posting_fact_by_effective_recorded_posting'
                    """)));
  }

  @Test
  void mutationWriterUpsertAccount_preservesImmutableBalanceAndDeclarationColumns() {
    Path databasePath = tempDirectory.resolve("upsert-account-columns.sqlite");

    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                staticBookAccess(databasePath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  SqliteMutationWriter.upsertAccount(
                      database,
                      new DeclaredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash"),
                          NormalBalance.DEBIT,
                          true,
                          Instant.parse("2026-04-07T10:15:30Z")));
                  SqliteMutationWriter.upsertAccount(
                      database,
                      new DeclaredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash Renamed"),
                          NormalBalance.CREDIT,
                          true,
                          Instant.parse("2026-04-08T10:15:30Z")));

                  assertEquals(
                      "Cash Renamed",
                      queryText(
                          database,
                          "select account_name from account where account_code = '1000'"));
                  assertEquals(
                      "DEBIT",
                      queryText(
                          database,
                          "select normal_balance from account where account_code = '1000'"));
                  assertEquals(
                      "2026-04-07T10:15:30Z",
                      queryText(
                          database, "select declared_at from account where account_code = '1000'"));
                }));
  }

  @Test
  void openBook_configuresOpenConnectionForHardeningAndDurability() throws Exception {
    Path databasePath = tempDirectory.resolve("connection-pragmas.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));

      assertEquals(1, queryInt(storeDatabase(postingFactStore), "pragma foreign_keys"));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma trusted_schema"));
    }
  }

  @Test
  void canonicalStrictSchema_rejectsNonLosslessTypeMismatches() {
    Path bookPath = tempDirectory.resolve("strict-datatype.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(database);
                  insertInitializedAtRow(database);
                  insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
                  insertPostingFactRow(database, "posting-1", "idem-1");

                  SqliteNativeException exception =
                      assertThrows(
                          SqliteNativeException.class,
                          () ->
                              database.executeStatement(
                                  """
                                  insert into journal_line (
                                      posting_id,
                                      line_order,
                                      account_code,
                                      entry_side,
                                      currency_code,
                                      amount
                                  ) values (
                                      'posting-1',
                                      'not-an-integer',
                                      '1000',
                                      'DEBIT',
                                      'EUR',
                                      '10.00'
                                  )
                                  """));

                  assertEquals(
                      SqliteNativeLibrary.SQLITE_CONSTRAINT_DATATYPE, exception.resultCode());
                  assertEquals("SQLITE_CONSTRAINT_DATATYPE", exception.resultName());
                  assertEquals(0, queryInt(database, "select count(*) from journal_line"));
                }));
  }

  @Test
  void findByPostingId_returnsEmptyWhenExistingBookHasNoMatchingPosting() {
    Path databasePath = tempDirectory.resolve("books").resolve("entity-a.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingFact);

      assertEquals(
          Optional.empty(), postingFactStore.findPosting(new PostingId("posting-missing")));
    }
  }

  @Test
  void commitAndFindByIdempotency_preservesReversalReference() {
    Path databasePath = tempDirectory.resolve("nested").resolve("entity-b.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact reversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(originalFact);
      postingFactStore.commit(reversalFact);

      assertEquals(
          Optional.of(reversalFact),
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-2")));
    }
  }

  @Test
  void commit_returnsDuplicateIdempotencyOutcome() {
    Path databasePath = tempDirectory.resolve("fingrind.sqlite");
    PostingFact postingFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(postingFact);

      assertEquals(
          rejected(new PostingRejection.DuplicateIdempotencyKey()),
          postingFactStore.commit(
              postingFact("posting-2", "idem-1", Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void commit_returnsDuplicateReversalTargetOutcome() {
    Path databasePath = tempDirectory.resolve("reversal.sqlite");
    PostingFact originalFact =
        postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty());
    PostingFact firstReversal =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("full reversal")));
    PostingFact secondReversal =
        postingFact(
            "posting-3",
            "idem-3",
            Optional.of(new ReversalReference(new PostingId("posting-1"))),
            Optional.of(new ReversalReason("another full reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(originalFact);
      postingFactStore.commit(firstReversal);

      assertEquals(
          rejected(new PostingRejection.ReversalAlreadyExists(new PostingId("posting-1"))),
          postingFactStore.commit(secondReversal));
      assertEquals(
          Optional.of(firstReversal), postingFactStore.findReversalFor(new PostingId("posting-1")));
    }
  }

  @Test
  void commit_throwsWhenPostingIdAlreadyExistsWithDifferentIdempotencyKey() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact("posting-1", "idem-2", Optional.empty(), Optional.empty())));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  void commit_rejectsMissingReversalTargetBeforeAnyForeignKeyWrite() {
    Path databasePath = tempDirectory.resolve("unexpected.sqlite");
    PostingFact invalidReversalFact =
        postingFact(
            "posting-2",
            "idem-2",
            Optional.of(new ReversalReference(new PostingId("posting-missing"))),
            Optional.of(new ReversalReason("operator reversal")));

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          rejected(new PostingRejection.ReversalTargetNotFound(new PostingId("posting-missing"))),
          postingFactStore.commit(invalidReversalFact));
    }
  }

  @Test
  void commit_rejectsBookPathWhoseParentIsAFile() throws IOException {
    Path fileParent = tempDirectory.resolve("not-a-directory");
    Files.writeString(fileParent, "nope", StandardCharsets.UTF_8);
    Path keyPath = tempDirectory.resolve("book-keys").resolve("entity.book-key");
    Files.createDirectories(keyPath.getParent());
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            new BookAccess(
                fileParent.resolve("entity.sqlite"),
                new BookAccess.PassphraseSource.KeyFile(keyPath)))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to create SQLite book directory."));
    }
  }

  @Test
  void findByIdempotency_throwsWhenExistingBookFileIsNotSqlite() throws IOException {
    Path databasePath = tempDirectory.resolve("not-a-database.sqlite");
    Files.writeString(databasePath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));

      assertInvalidPlaintextBookFailure(exception);
    }
  }

  @Test
  void findByIdempotency_throwsWhenBookPathPointsAtDirectory() throws IOException {
    Path databasePath = tempDirectory.resolve("book-directory");
    Files.createDirectories(databasePath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));

      assertTrue(exception.getMessage().contains("Failed to open SQLite book connection."));
    }
  }

  @Test
  void readSchema_mapsIoFailure() {
    assertThrows(
        IllegalStateException.class,
        () -> SqliteBookSchemaBootstrap.readSchema(SqlitePostingFactStoreTest::failingInputStream));
  }

  @Test
  void initializeBook_executesWholeSchemaScriptWithoutStatementSplitting() {
    Path bookPath = tempDirectory.resolve("schema-script.sqlite");
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  SqliteBookSchemaBootstrap.initializeBook(
                      database,
                      () ->
                          new ByteArrayInputStream(
                              """
                              create table sample (
                                  id integer primary key,
                                  note text not null
                              );
                              create table sample_audit (
                                  note text not null
                              );
                              -- comment with semicolon;
                              create trigger sample_after_insert
                              after insert on sample
                              begin
                                  insert into sample_audit (note) values ('semi;colon');
                              end;
                              """
                                  .getBytes(StandardCharsets.UTF_8)));

                  database.executeStatement("insert into sample (id, note) values (1, 'ok')");

                  try (SqliteNativeStatement statement =
                      SqliteNativeLibrary.prepare(database, "select note from sample_audit")) {
                    assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
                    assertEquals("semi;colon", statement.columnText(0));
                    assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
                  }
                }));
  }

  @Test
  void cachedValue_loadsAndStoresValueWhenCacheIsEmpty() {
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals("loaded", SqliteBookSchemaBootstrap.cachedValue(schemaCache, () -> "loaded"));
    assertEquals("loaded", schemaCache.get());
  }

  @Test
  void cachedValue_returnsExistingValueWithoutCallingLoader() {
    AtomicReference<String> schemaCache = new AtomicReference<>("cached");

    assertEquals(
        "cached",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              throw new AssertionError("loader should not run when cache already has a value");
            }));
  }

  @Test
  void cachedValue_returnsAlreadyPublishedValueWhenAnotherLoadWinsTheRace() {
    AtomicReference<String> schemaCache = new AtomicReference<>();

    assertEquals(
        "published-first",
        SqliteBookSchemaBootstrap.cachedValue(
            schemaCache,
            () -> {
              schemaCache.set("published-first");
              return "loaded-late";
            }));
    assertEquals("published-first", schemaCache.get());
  }

  @Test
  void close_isIdempotent() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("close-ok.sqlite")))) {
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_afterDatabaseOpenRemainsIdempotent() throws Exception {
    Path bookPath = tempDirectory.resolve("close-opened.sqlite");
    initializeBookOnDisk(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      assertDoesNotThrow(() -> postingFactStore.listAccounts(firstAccountPage()));
      assertDoesNotThrow(postingFactStore::close);
      assertDoesNotThrow(postingFactStore::close);
    }
  }

  @Test
  void close_zeroizesPendingPassphraseWhenDatabaseWasNeverOpened() throws Exception {
    SqliteBookPassphrase passphrase =
        SqliteBookPassphrase.fromCharacters(
            "test close pending passphrase", TEST_BOOK_KEY.toCharArray());
    byte[] expectedZeroes = new byte[TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length];

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(tempDirectory.resolve("never-opened.sqlite"), passphrase)) {
      postingFactStore.close();
    }

    assertArrayEquals(expectedZeroes, passphraseBytes(passphrase));
  }

  @Test
  void storeRetainsStableOpenFailureAfterPassphraseConsumption() throws Exception {
    Path invalidBookPath = tempDirectory.resolve("invalid-retry.sqlite");
    Files.writeString(invalidBookPath, "not sqlite", StandardCharsets.UTF_8);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(invalidBookPath))) {
      IllegalStateException firstFailure =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);
      IllegalStateException secondFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertInvalidPlaintextBookFailure(firstFailure);
      assertSame(firstFailure, secondFailure);
    }
  }

  @Test
  void rekeyBook_rotatesPassphraseAndPreservesReadableState() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-book.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement store passphrase", "rotated-store-key".toCharArray())) {
        assertEquals(
            new dev.erst.fingrind.contract.RekeyBookResult.Rekeyed(
                bookPath.toAbsolutePath().normalize()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
    }

    try (SqlitePostingFactStore oldKeyStore = new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> oldKeyStore.listAccounts(firstAccountPage()));
      assertInvalidPlaintextBookFailure(exception);
    }

    try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "replacement store passphrase", "rotated-store-key".toCharArray());
        SqlitePostingFactStore rotatedStore =
            new SqlitePostingFactStore(
                bookPath, replacementPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      assertEquals(
          List.of(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z")),
              new DeclaredAccount(
                  new AccountCode("2000"),
                  new AccountName("Revenue"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          listAccounts(rotatedStore));
      assertEquals("delete", queryText(storeDatabase(rotatedStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(rotatedStore), "pragma synchronous"));
      assertEquals(1, queryInt(storeDatabase(rotatedStore), "pragma query_only"));
    }
  }

  @Test
  void rekeyBook_rejectsUninitializedBooksAndReadOnlyMutation() throws Exception {
    Path missingBookPath = tempDirectory.resolve("rekey-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement missing book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new dev.erst.fingrind.contract.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
      assertArrayEquals(
          new byte["rotated-store-key".getBytes(StandardCharsets.UTF_8).length],
          passphraseBytes(replacementPassphrase));
    }

    Path blankBookPath = tempDirectory.resolve("rekey-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement blank book", "rotated-store-key".toCharArray());
      try (replacementPassphrase) {
        assertEquals(
            new dev.erst.fingrind.contract.RekeyBookResult.Rejected(
                new BookAdministrationRejection.BookNotInitialized()),
            postingFactStore.rekeyBook(replacementPassphrase));
      }
      assertArrayEquals(
          new byte["rotated-store-key".getBytes(StandardCharsets.UTF_8).length],
          passphraseBytes(replacementPassphrase));
    }

    Path initializedBookPath = tempDirectory.resolve("rekey-read-only.sqlite");
    initializeBookOnDisk(initializedBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "read-only book passphrase", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                initializedBookPath, bookPassphrase, SqliteStoreAccessMode.READ_ONLY)) {
      try (SqliteBookPassphrase replacementPassphrase =
          SqliteBookPassphrase.fromCharacters(
              "replacement read-only book", "rotated-store-key".toCharArray())) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.rekeyBook(replacementPassphrase));
        assertEquals(
            "This FinGrind SQLite session is read-only and cannot mutate the book.",
            exception.getMessage());
      }
    }
  }

  @Test
  void accessModes_enforceWritableBoundariesAndQueryOnlyPolicy() throws Exception {
    assertEquals(1, SqliteStoreAccessMode.READ_ONLY.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_EXISTING.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.READ_WRITE_CREATE.queryOnlyPragmaValue());
    assertEquals(0, SqliteStoreAccessMode.PLAN_EXECUTION.queryOnlyPragmaValue());

    assertThrows(
        IllegalStateException.class, SqliteStoreAccessMode.READ_ONLY::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableMutation);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableMutation);

    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_ONLY::requireWritableInitialization);
    assertThrows(
        IllegalStateException.class,
        SqliteStoreAccessMode.READ_WRITE_EXISTING::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.READ_WRITE_CREATE::requireWritableInitialization);
    assertDoesNotThrow(SqliteStoreAccessMode.PLAN_EXECUTION::requireWritableInitialization);
    assertTrue(SqliteStoreAccessMode.PLAN_EXECUTION.preservesMissingBookStateUntilMutation());
    assertTrue(SqliteStoreAccessMode.READ_WRITE_CREATE.preservesMissingBookStateUntilMutation());

    Path existingBookPath = tempDirectory.resolve("read-write-existing.sqlite");
    initializeBookOnDisk(existingBookPath);
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                existingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("3000"),
                  new AccountName("Equity"),
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          postingFactStore.declareAccount(
              new AccountCode("3000"),
              new AccountName("Equity"),
              NormalBalance.CREDIT,
              Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals("delete", queryText(storeDatabase(postingFactStore), "pragma journal_mode"));
      assertEquals(3, queryInt(storeDatabase(postingFactStore), "pragma synchronous"));
      assertEquals(0, queryInt(storeDatabase(postingFactStore), "pragma query_only"));
    }

    Path missingBookPath = tempDirectory.resolve("read-write-existing-missing.sqlite");
    try (SqliteBookPassphrase bookPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "existing access mode missing", TEST_BOOK_KEY.toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                missingBookPath, bookPassphrase, SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          "This FinGrind SQLite session cannot initialize or create a book file.",
          exception.getMessage());
    }
  }

  @Test
  void helperBoundaries_rejectUnsafeShapesAndWrapNativeFailures() throws Exception {
    SqliteBookStateReader bookStateReader =
        new SqliteBookStateReader(
            SqliteBookContract.APPLICATION_ID,
            SqliteBookContract.FORMAT_VERSION,
            "account",
            "book_meta",
            "journal_line",
            "posting_fact");

    Path blankBookPath = tempDirectory.resolve("helper-blank.sqlite");
    createEmptySqliteFile(blankBookPath);
    withStandaloneDatabase(
        bookAccess(blankBookPath),
        database -> {
          IllegalStateException emptyQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleInt(database, "select 1 where 0"));
          assertEquals(
              "SQLite integer query returned no rows: select 1 where 0",
              emptyQueryException.getMessage());

          IllegalStateException emptyTextQueryException =
              assertThrows(
                  IllegalStateException.class,
                  () -> SqliteStatementQueries.querySingleText(database, "select 'x' where 0"));
          assertEquals(
              "SQLite text query returned no rows: select 'x' where 0",
              emptyTextQueryException.getMessage());

          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(blankBookPath))) {
            IllegalStateException blankException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not initialized as a FinGrind book.",
                blankException.getMessage());
          }
        });

    Path initializedBookPath = tempDirectory.resolve("helper-initialized.sqlite");
    initializeBookOnDisk(initializedBookPath);
    withStandaloneDatabase(
        bookAccess(initializedBookPath),
        database -> {
          IllegalStateException multiRowException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.queryOptionalInt(
                          database, "select 1 union all select 2"));
          assertEquals(
              "SQLite integer query returned more than one row: select 1 union all select 2",
              multiRowException.getMessage());

          IllegalStateException multiRowTextException =
              assertThrows(
                  IllegalStateException.class,
                  () ->
                      SqliteStatementQueries.querySingleText(
                          database, "select 'x' union all select 'y'"));
          assertEquals(
              "SQLite text query returned more than one row: select 'x' union all select 'y'",
              multiRowTextException.getMessage());
          assertEquals(
              OptionalInt.of(1), SqliteStatementQueries.queryOptionalInt(database, "select 1"));
          assertEquals("x", SqliteStatementQueries.querySingleText(database, "select 'x'"));
          assertEquals("INITIALIZED_FINGRIND", bookStateReader.bookState(database).toString());
        });

    Path foreignBookPath = tempDirectory.resolve("helper-foreign.sqlite");
    createPostingFactOnlyBook(foreignBookPath);
    withStandaloneDatabase(
        bookAccess(foreignBookPath),
        database -> {
          try (SqlitePostingFactStore postingFactStore =
              new SqlitePostingFactStore(bookAccess(foreignBookPath))) {
            IllegalStateException foreignException =
                assertThrows(
                    IllegalStateException.class,
                    () -> postingFactStore.requireInitializedBook(database));
            assertEquals(
                "The selected SQLite file is not a FinGrind book.", foreignException.getMessage());
          }
        });

    Path unsupportedBookPath = tempDirectory.resolve("helper-unsupported.sqlite");
    initializeBookOnDisk(unsupportedBookPath);
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> database.executeStatement("pragma user_version = 2"));
    withStandaloneDatabase(
        bookAccess(unsupportedBookPath),
        database -> {
          assertEquals(
              "UNSUPPORTED_FINGRIND_VERSION", bookStateReader.bookState(database).toString());
        });

    Path incompleteBookPath = tempDirectory.resolve("helper-incomplete.sqlite");
    createSchemaOnlyBook(incompleteBookPath);
    withStandaloneDatabase(
        bookAccess(incompleteBookPath),
        database -> {
          assertEquals("INCOMPLETE_FINGRIND", bookStateReader.bookState(database).toString());
        });

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(blankBookPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeLibrary.open(bookAccess(blankBookPath)));
      assertEquals(
          Optional.of(new PostingRejection.BookNotInitialized()),
          PostingValidation.rejectionFor(
              postingDraft("posting-helper", "idem-helper", Optional.empty(), Optional.empty()),
              new SqliteTransactionValidationBook(
                  storeDatabase(postingFactStore), postingFactStore.postingReader())));
    }

    Path staleBookPath = tempDirectory.resolve("find-one-stale.sqlite");
    createEmptySqliteFile(staleBookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(staleBookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(staleBookPath));
      IllegalStateException failure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-helper")));
      assertTrue(failure.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void loadInitializedAt_returnsEmptyWithoutMarkerAndValueWhenPresent() throws Exception {
    Path missingMarkerPath = tempDirectory.resolve("initialized-at-missing.sqlite");
    createSchemaOnlyBook(missingMarkerPath);
    Optional<Instant> missingInitializedAt =
        withStandaloneDatabaseResult(
            bookAccess(missingMarkerPath), SqliteStatementQueries::loadInitializedAt);
    assertEquals(Optional.empty(), missingInitializedAt);

    Path presentMarkerPath = tempDirectory.resolve("initialized-at-present.sqlite");
    initializeBookOnDisk(presentMarkerPath);
    Optional<Instant> presentInitializedAt =
        withStandaloneDatabaseResult(
            bookAccess(presentMarkerPath), SqliteStatementQueries::loadInitializedAt);
    assertEquals(Optional.of(Instant.parse("2026-04-07T10:15:30Z")), presentInitializedAt);
  }

  @Test
  void transactionValidationBook_wrapsNativeFailuresForStateAndAccountLookups() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-stale.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              staleDatabaseHandle(bookPath), postingFactStore.postingReader());

      IllegalStateException initializedFailure =
          assertThrows(IllegalStateException.class, validationBook::isInitialized);
      assertTrue(initializedFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException accountFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findAccount(new AccountCode("1000")));
      assertTrue(accountFailure.getMessage().contains("Failed to query SQLite book."));

      IllegalStateException postingFailure =
          assertThrows(
              IllegalStateException.class,
              () -> validationBook.findExistingPosting(new IdempotencyKey("idem-1")));
      assertTrue(postingFailure.getMessage().contains("Failed to query SQLite book."));
    }
  }

  @Test
  void transactionValidationBook_findsDeclaredAccountsThroughBatchLookup() throws Exception {
    Path bookPath = tempDirectory.resolve("validation-success.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      SqliteTransactionValidationBook validationBook =
          new SqliteTransactionValidationBook(
              storeDatabase(postingFactStore), postingFactStore.postingReader());

      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          validationBook.findAccount(new AccountCode("1000")));
    }
  }

  @Test
  void rekeyBook_clearsCachedDatabaseHandleWhenReopenFailsAfterRotation() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-reopen-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3OpenV2HandleForTesting(
              constantMethodHandle(
                  14, MemorySegment.class, MemorySegment.class, int.class, MemorySegment.class))) {
        try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey reopen failure", "rotated-store-key".toCharArray())) {
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> postingFactStore.rekeyBook(replacementPassphrase));

          assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
          assertNull(storeDatabase(postingFactStore));
        }
      }
    }
  }

  @Test
  void rekeyBook_preservesOpenDatabaseWhenNativeRekeyFailsBeforeClose() throws Exception {
    Path bookPath = tempDirectory.resolve("rekey-native-failure.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      try (AutoCloseable ignored =
          SqliteNativeLibrary.overrideSqlite3RekeyHandleForTesting(
              constantMethodHandle(14, MemorySegment.class, MemorySegment.class, int.class))) {
        try (SqliteBookPassphrase replacementPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "rekey native failure", "rotated-store-key".toCharArray())) {
          IllegalStateException exception =
              assertThrows(
                  IllegalStateException.class,
                  () -> postingFactStore.rekeyBook(replacementPassphrase));

          assertTrue(exception.getMessage().contains("Failed to rekey SQLite book."));
          assertNotNull(storeDatabase(postingFactStore));
        }
      }
    }
  }

  @Test
  void activeNativeDatabase_returnsPublishedSessionHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("active-native-database.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      initializeBookWithDefaultAccounts(postingFactStore);

      assertEquals(storeDatabase(postingFactStore), postingFactStore.activeNativeDatabase());
    }
  }

  @Test
  void assertOpenConfiguration_rejectsHardeningDrift() throws Exception {
    assertOpenConfigurationFailure(
        "pragma foreign_keys = off", "SQLite connection failed to keep foreign_keys enabled.");
    assertOpenConfigurationFailure(
        "pragma journal_mode = wal", "SQLite connection failed to enforce journal_mode=DELETE.");
    assertOpenConfigurationFailure(
        "pragma synchronous = normal", "SQLite connection failed to enforce synchronous=EXTRA.");
    assertOpenConfigurationFailure(
        "pragma trusted_schema = on", "SQLite connection failed to disable trusted_schema.");
    assertOpenConfigurationFailure(
        "pragma secure_delete = off", "SQLite connection failed to enable secure_delete.");
    assertOpenConfigurationFailure(
        "pragma temp_store = file", "SQLite connection failed to force temp_store=MEMORY.");
    assertOpenConfigurationFailure(
        "pragma query_only = on",
        "SQLite connection failed to enforce the expected query_only setting.");
  }

  @Test
  void requireOptionalPragmaValue_enforcesPresentUnexpectedValuesOnly() {
    assertDoesNotThrow(
        () ->
            SqliteConnectionConfigurer.requireOptionalPragmaValue(
                OptionalInt.empty(), 1, "should stay optional"));
    assertDoesNotThrow(
        () ->
            SqliteConnectionConfigurer.requireOptionalPragmaValue(
                OptionalInt.of(1), 1, "should accept expected value"));

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                SqliteConnectionConfigurer.requireOptionalPragmaValue(
                    OptionalInt.of(0),
                    1,
                    "SQLite connection failed to enable memory_security=fill."));

    assertEquals(
        "SQLite connection failed to enable memory_security=fill.", exception.getMessage());
  }

  @Test
  void bookStateHelpers_coverCanonicalAndMarkerShortCircuits() throws Exception {
    SqliteBookStateReader bookStateReader =
        new SqliteBookStateReader(
            SqliteBookContract.APPLICATION_ID,
            SqliteBookContract.FORMAT_VERSION,
            "account",
            "book_meta",
            "journal_line",
            "posting_fact");

    Path noMetaPath = tempDirectory.resolve("fgrd-no-meta.sqlite");
    createPartialFinGrindBook(noMetaPath, false, false, false, false, false);
    BookStateProbe noMetaProbe =
        withStandaloneDatabaseResult(
            bookAccess(noMetaPath),
            database ->
                new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString()));
    assertFalse(noMetaProbe.hasCanonicalTables());
    assertFalse(noMetaProbe.hasInitializedMarker());
    assertEquals("INCOMPLETE_FINGRIND", noMetaProbe.bookState());

    Path noAccountPath = tempDirectory.resolve("fgrd-no-account.sqlite");
    createPartialFinGrindBook(noAccountPath, true, false, false, false, false);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noAccountPath), bookStateReader::hasCanonicalTables));

    Path noPostingPath = tempDirectory.resolve("fgrd-no-posting.sqlite");
    createPartialFinGrindBook(noPostingPath, true, true, false, false, false);
    assertFalse(
        withStandaloneDatabaseResult(
            bookAccess(noPostingPath), bookStateReader::hasCanonicalTables));

    Path noJournalLinePath = tempDirectory.resolve("fgrd-no-journal-line.sqlite");
    createPartialFinGrindBook(noJournalLinePath, true, true, true, false, false);
    BookStateProbe noJournalLineProbe =
        withStandaloneDatabaseResult(
            bookAccess(noJournalLinePath),
            database ->
                new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString()));
    assertFalse(noJournalLineProbe.hasCanonicalTables());
    assertEquals("INCOMPLETE_FINGRIND", noJournalLineProbe.bookState());

    Path initializedPath = tempDirectory.resolve("fgrd-initialized-short-circuit.sqlite");
    initializeBookOnDisk(initializedPath);
    BookStateProbe initializedProbe =
        withStandaloneDatabaseResult(
            bookAccess(initializedPath),
            database -> {
              try (SqlitePostingFactStore postingFactStore =
                  new SqlitePostingFactStore(bookAccess(initializedPath))) {
                assertDoesNotThrow(() -> postingFactStore.requireInitializedBook(database));
                return new BookStateProbe(
                    bookStateReader.hasCanonicalTables(database),
                    bookStateReader.hasInitializedMarker(database),
                    bookStateReader.bookState(database).toString());
              }
            });
    assertTrue(initializedProbe.hasCanonicalTables());
    assertTrue(initializedProbe.hasInitializedMarker());
    assertEquals("INITIALIZED_FINGRIND", initializedProbe.bookState());

    Path versionOnlyPath = tempDirectory.resolve("foreign-version-only.sqlite");
    withStandaloneDatabase(
        bookAccess(versionOnlyPath),
        database -> database.executeStatement("pragma user_version = 1"));
    String versionOnlyBookState =
        withStandaloneDatabaseResult(
            bookAccess(versionOnlyPath),
            database -> bookStateReader.bookState(database).toString());
    assertEquals("FOREIGN_SQLITE", versionOnlyBookState);
  }

  @Test
  void constructor_rejectsNonKeyFileAccessSelection() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(
                        tempDirectory.resolve("stdin-access.sqlite"),
                        BookAccess.PassphraseSource.StandardInput.INSTANCE)));

    assertEquals(
        "SQLite same-package file-backed stores require a --book-key-file access selection.",
        exception.getMessage());
  }

  @Test
  void constructor_rejectsInvalidKeyFilePayloadBeforeStoreCreation() throws Exception {
    Path bookPath = tempDirectory.resolve("invalid-key-payload-store.sqlite");
    Path keyPath = tempDirectory.resolve("invalid-key-payload-store.key");
    writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
    Files.write(keyPath, new byte[] {(byte) 0xFF});

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () ->
                new SqlitePostingFactStore(
                    new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath))));

    assertTrue(exception.getMessage().contains("must contain a UTF-8 passphrase"));
  }

  @Test
  void passphraseFor_loadsKeyFileBackedAccessSelection() throws Exception {
    Path keyFile = tempDirectory.resolve("book-passphrase.key");
    writeSecureKeyFile(keyFile, TEST_BOOK_KEY);

    try (SqliteBookPassphrase passphrase =
        SqlitePostingFactStore.passphraseDecisionFor(
                new BookAccess(
                    tempDirectory.resolve("book-passphrase.sqlite"),
                    new BookAccess.PassphraseSource.KeyFile(keyFile)))
            .requireAccepted()) {
      assertEquals(keyFile.toAbsolutePath().normalize().toString(), passphrase.sourceDescription());
      assertEquals(TEST_BOOK_KEY.getBytes(StandardCharsets.UTF_8).length, passphrase.byteLength());
    }
  }

  @Test
  void lifecyclePrime_coversPublishedDatabaseAndAuthenticationRejectionBranches() throws Exception {
    Path publishedPath = tempDirectory.resolve("prime-published.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(publishedPath))) {
      setStoreDatabase(postingFactStore, SqliteNativeLibrary.open(bookAccess(publishedPath)));
      assertSame(
          postingFactStore.lifecycle(), postingFactStore.lifecycle().prime().requireAccepted());
    }

    Path existingPath = tempDirectory.resolve("prime-existing.sqlite");
    initializeBookOnDisk(existingPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(existingPath))) {
      assertSame(
          postingFactStore.lifecycle(), postingFactStore.lifecycle().prime().requireAccepted());
    }

    Path existingReadWriteExistingPath =
        tempDirectory.resolve("prime-existing-read-write-existing.sqlite");
    initializeBookOnDisk(existingReadWriteExistingPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            bookAccess(existingReadWriteExistingPath), SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      assertSame(
          postingFactStore.lifecycle(), postingFactStore.lifecycle().prime().requireAccepted());
    }

    Path initializedPath = tempDirectory.resolve("prime-wrong-passphrase.sqlite");
    initializeBookOnDisk(initializedPath);
    try (SqliteBookPassphrase wrongPassphrase =
            SqliteBookPassphrase.fromCharacters(
                "prime wrong passphrase", "wrong-passphrase".toCharArray());
        SqlitePostingFactStore postingFactStore =
            new SqlitePostingFactStore(
                initializedPath, wrongPassphrase, SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      ContractDecision<SqliteStoreLifecycle> decision = postingFactStore.lifecycle().prime();
      switch (decision) {
        case ContractDecision.Accepted<SqliteStoreLifecycle> _ ->
            throw new AssertionError("Expected lifecycle priming to be rejected.");
        case ContractDecision.Rejected<SqliteStoreLifecycle>(var failure) ->
            assertEquals(
                ContractErrors.Descriptor.BOOK_AUTHENTICATION_FAILED.code(), failure.code());
      }
    }
  }

  @Test
  void lifecycleTransactionBranches_coverExistingCreateAndDetachedRollbackPaths() throws Exception {
    Path existingPath = tempDirectory.resolve("lifecycle-existing-create.sqlite");
    initializeBookOnDisk(existingPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(existingPath))) {
      postingFactStore.beginLedgerPlanTransaction();

      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));

      try (SqliteNativeDatabase detachedDatabase = requireStoreDatabase(postingFactStore)) {
        setStoreDatabase(postingFactStore, null);
        assertNotNull(detachedDatabase.handle());
        assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
        assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
        assertFalse(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      }
    }

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            bookAccess(existingPath), SqliteStoreAccessMode.READ_WRITE_EXISTING)) {
      postingFactStore.beginLedgerPlanTransaction();
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionActive"));
      assertTrue(storeBooleanField(postingFactStore, "ledgerPlanTransactionBegunInDatabase"));
      assertDoesNotThrow(postingFactStore::rollbackLedgerPlanTransaction);
    }
  }

  @Test
  void configureOpenedDatabase_closesUnconfiguredDatabaseQuietlyWhenPragmasFail() throws Exception {
    SqliteNativeException exception =
        assertThrows(
            SqliteNativeException.class,
            () ->
                SqliteConnectionConfigurer.configureOpenedDatabase(
                    staleDatabaseHandle(tempDirectory.resolve("stale.sqlite")),
                    SqliteStoreAccessMode.READ_WRITE_CREATE));

    assertFalse(exception.getMessage().isBlank());
  }

  @Test
  void closeAfterConfigurationFailure_closesOpenDatabase() throws Exception {
    Path bookPath = tempDirectory.resolve("configured-close.sqlite");
    try (SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess(bookPath))) {
      assertDoesNotThrow(() -> SqliteConnectionConfigurer.closeAfterConfigurationFailure(database));
    }
  }

  @Test
  void closeAfterConfigurationFailure_ignoresNativeCloseFailure() throws Exception {
    assertDoesNotThrow(
        () ->
            SqliteConnectionConfigurer.closeAfterConfigurationFailure(
                staleDatabaseHandle(tempDirectory.resolve("stale-close.sqlite"))));
  }

  @Test
  void takeBookPassphrase_rejectsSecondConsumption() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("passphrase-consumption.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test passphrase consumption", TEST_BOOK_KEY.toCharArray()))) {
      try (SqliteBookPassphrase ignored =
          SqliteStoreTestAccess.takePendingPassphrase(postingFactStore)) {
        IllegalStateException exception =
            assertThrows(
                IllegalStateException.class,
                () -> SqliteStoreTestAccess.takePendingPassphrase(postingFactStore));

        assertEquals("SQLite book passphrase is no longer available.", exception.getMessage());
      }
    }
  }

  @Test
  void openBook_remembersConsumedPassphraseFailureAsTerminalState() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(
            tempDirectory.resolve("consumed-passphrase.sqlite"),
            SqliteBookPassphrase.fromCharacters(
                "test consumed passphrase", TEST_BOOK_KEY.toCharArray()))) {
      try (SqliteBookPassphrase ignored =
          SqliteStoreTestAccess.takePendingPassphrase(postingFactStore)) {
        IllegalStateException firstFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
        IllegalStateException secondFailure =
            assertThrows(
                IllegalStateException.class,
                () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

        assertEquals("SQLite book passphrase is no longer available.", firstFailure.getMessage());
        assertSame(firstFailure, secondFailure);
      }
    }
  }

  @Test
  void close_rejectsFurtherUse() {
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(tempDirectory.resolve("closed.sqlite")))) {
      postingFactStore.close();

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-closed")));

      assertEquals("SQLite book session is already closed.", exception.getMessage());
    }
  }

  @Test
  void close_wrapsNativeDatabaseCloseFailure() throws Exception {
    Path bookPath = tempDirectory.resolve("close-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::close);

      assertTrue(exception.getMessage().contains("Failed to close SQLite book connection."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void openBook_wrapsInitializationFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("schema-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to initialize SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void commit_ignoresRollbackFailureWhenPrimaryFailureAlreadyExists() throws Exception {
    Path bookPath = tempDirectory.resolve("rollback-native-failure.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));

      assertTrue(exception.getMessage().contains("Failed to commit SQLite posting fact."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void commit_primaryKeyConflictWithFirstReversalLeavesConstraintAsPrimaryFailure() {
    Path databasePath = tempDirectory.resolve("duplicate-posting-id-reversal.sqlite");

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      postingFactStore.commit(
          postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.commit(
                      postingFact(
                          "posting-1",
                          "idem-2",
                          Optional.of(new ReversalReference(new PostingId("posting-1"))),
                          Optional.of(new ReversalReason("full reversal")))));

      assertTrue(exception.getMessage().contains("PRIMARYKEY"));
    }
  }

  @Test
  void findByIdempotency_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("query-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-query")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void isInitialized_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("initialized-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(IllegalStateException.class, postingFactStore::isInitialized);

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccount_wrapsQueryFailureFromStaleInitializedDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("account-stale-initialized-handle.sqlite");
    initializeBookOnDisk(bookPath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));
      setStoreCachedBookState(
          postingFactStore,
          new SqliteBookStateSnapshot(
              SqliteBookContract.APPLICATION_ID,
              SqliteBookContract.FORMAT_VERSION,
              SqliteBookState.INITIALIZED_FINGRIND));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccount(new AccountCode("1000")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("accounts-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findAccounts(Set.of(new AccountCode("1000"))));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void declareAccount_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("declare-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.declareAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      Instant.parse("2026-04-07T10:15:30Z")));

      assertTrue(exception.getMessage().contains("Failed to declare SQLite book account."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void listAccounts_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("list-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class, () -> postingFactStore.listAccounts(firstAccountPage()));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findByPostingId_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("posting-id-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findReversalFor_wrapsQueryFailureFromStaleDatabaseHandle() throws Exception {
    Path bookPath = tempDirectory.resolve("reversal-stale-handle.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(bookPath));

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));

      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      setStoreDatabase(postingFactStore, null);
    }
  }

  @Test
  void findByIdempotency_rejectsForeignSqliteFileWithPostingLikeSchema() {
    Path bookPath = tempDirectory.resolve("missing-line-table.sqlite");
    createPostingFactOnlyBook(bookPath);

    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(bookPath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("idem-partial")));

      assertEquals("The selected SQLite file is not a FinGrind book.", exception.getMessage());
    }
  }

  @Test
  void executeFindOnePosting_closesStatementWhenRowMappingFails() throws Exception {
    Path bookPath = tempDirectory.resolve("row-mapping-failure.sqlite");
    createEmptySqliteFile(bookPath);
    assertDoesNotThrow(
        () ->
            withStandaloneDatabase(
                bookAccess(bookPath),
                database -> {
                  assertThrows(
                      NullPointerException.class,
                      () ->
                          SqliteStatementQueries.findOnePosting(
                              database,
                              "select null as posting_id",
                              statement -> {},
                              postingId -> List.of()));
                }));
  }

  private static InputStream failingInputStream() {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("boom");
      }

      @Override
      public int read(byte[] buffer, int offset, int length) throws IOException {
        throw new IOException("boom");
      }
    };
  }

  private static int queryInt(SqliteNativeDatabase database, String sql) {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
      int value = statement.columnInt(0);
      assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
      return value;
    }
  }

  private static String queryText(SqliteNativeDatabase database, String sql) {
    try (SqliteNativeStatement statement = SqliteNativeLibrary.prepare(database, sql)) {
      assertEquals(SqliteNativeLibrary.SQLITE_ROW, statement.step());
      String value = statement.columnText(0);
      assertEquals(SqliteNativeLibrary.SQLITE_DONE, statement.step());
      return value;
    }
  }

  private static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    return new PostingFact(
        new PostingId(postingId),
        journalEntry(reversalReference),
        postingLineage(reversalReference, reason),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            Instant.parse("2026-04-07T10:15:30Z"),
            SourceChannel.CLI));
  }

  private static PostingFact postingFact(
      String postingId,
      String idempotencyKey,
      LocalDate effectiveDate,
      Instant recordedAt,
      List<JournalLine> lines) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(effectiveDate, lines),
        PostingLineage.direct(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-" + postingId),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.of(new CorrelationId("corr-1"))),
            recordedAt,
            SourceChannel.CLI));
  }

  private static PostingLineage postingLineage(
      Optional<ReversalReference> reversalReference, Optional<ReversalReason> reason) {
    if (reversalReference.isEmpty()) {
      return PostingLineage.direct();
    }
    return PostingLineage.reversal(reversalReference.orElseThrow(), reason.orElseThrow());
  }

  private static void initializeBookWithDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"));
    declareDefaultAccounts(postingFactStore);
  }

  private static void declareDefaultAccounts(SqlitePostingFactStore postingFactStore) {
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("1000"),
                new AccountName("Cash"),
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            NormalBalance.DEBIT,
            Instant.parse("2026-04-07T10:15:30Z")));
    assertEquals(
        new DeclareAccountResult.Declared(
            new DeclaredAccount(
                new AccountCode("2000"),
                new AccountName("Revenue"),
                NormalBalance.CREDIT,
                true,
                Instant.parse("2026-04-07T10:15:30Z"))),
        postingFactStore.declareAccount(
            new AccountCode("2000"),
            new AccountName("Revenue"),
            NormalBalance.CREDIT,
            Instant.parse("2026-04-07T10:15:30Z")));
  }

  private static JournalEntry journalEntry(Optional<ReversalReference> reversalReference) {
    if (reversalReference.isPresent()) {
      return new JournalEntry(
          LocalDate.parse("2026-04-07"),
          List.of(
              line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
              line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
    }
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }

  private static JournalLine line(
      String accountCode, JournalLine.EntrySide side, String currencyCode, String amount) {
    return new JournalLine(new AccountCode(accountCode), side, money(currencyCode, amount));
  }

  private static Money money(String currencyCode, String amount) {
    return new Money(new CurrencyCode(currencyCode), new BigDecimal(amount));
  }

  private static void insertPostingFactRow(
      SqliteNativeDatabase database, String postingId, String idempotencyKey) {
    database.executeStatement(
        """
        insert into posting_fact (
            posting_id,
            effective_date,
            recorded_at,
            actor_id,
            actor_type,
            command_id,
            idempotency_key,
            causation_id,
            correlation_id,
            reason,
            source_channel,
            prior_posting_id
        ) values (
            '%s',
            '2026-04-07',
            '2026-04-07T10:15:30Z',
            'actor-1',
            'AGENT',
            'command-%s',
            '%s',
            'cause-1',
            null,
            null,
            'CLI',
            null
        )
        """
            .formatted(postingId, postingId, idempotencyKey));
  }

  private static void insertInitializedAtRow(SqliteNativeDatabase database) {
    database.executeStatement(
        """
        insert into book_meta (key, value)
        values ('initialized_at', '2026-04-07T10:15:30Z')
        """);
  }

  private static void insertAccountRow(
      SqliteNativeDatabase database,
      String accountCode,
      String accountName,
      String normalBalance,
      int active,
      String declaredAt) {
    database.executeStatement(
        """
        insert into account (
            account_code,
            account_name,
            normal_balance,
            active,
            declared_at
        ) values (
            '%s',
            '%s',
            '%s',
            %d,
            '%s'
        )
        """
            .formatted(accountCode, accountName, normalBalance, active, declaredAt));
  }

  private SqliteNativeDatabase staleDatabaseHandle(Path bookPath) throws IOException {
    if (bookPath.getParent() != null) {
      Files.createDirectories(bookPath.getParent());
    }
    if (Files.notExists(bookPath)) {
      Files.write(bookPath, new byte[0]);
    }
    return new ThrowingSqliteNativeDatabase();
  }

  /** Same-package deterministic native-failure double that never touches freed SQLite memory. */
  private static final class ClosingSqliteNativeDatabase extends SqliteNativeDatabase {
    private boolean closeAttempted;

    private ClosingSqliteNativeDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    public void close() {
      closeAttempted = true;
    }

    private boolean closeAttempted() {
      return closeAttempted;
    }
  }

  /** Same-package deterministic native-failure double that never touches freed SQLite memory. */
  private static final class ThrowingSqliteNativeDatabase extends SqliteNativeDatabase {
    private boolean closeAttempted;
    private boolean closeFailed;

    private ThrowingSqliteNativeDatabase() {
      super(MemorySegment.NULL);
    }

    @Override
    SqliteNativeStatement prepare(String sql) {
      throw simulatedNativeFailure("prepare a SQLite statement");
    }

    @Override
    void executeStatement(String sql) {
      throw simulatedNativeFailure("execute a SQLite statement");
    }

    @Override
    void executeScript(String sql) {
      throw simulatedNativeFailure("execute a SQLite script");
    }

    @Override
    public void close() {
      closeAttempted = true;
      if (!closeFailed) {
        closeFailed = true;
        throw simulatedNativeFailure("close a SQLite database");
      }
    }

    private boolean closeAttempted() {
      return closeAttempted;
    }

    private static SqliteNativeException simulatedNativeFailure(String operation) {
      return new SqliteNativeException(
          14, "Simulated SQLite native failure while attempting to " + operation + ".");
    }
  }

  private static void createPostingFactOnlyBook(Path bookPath) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement(
              """
              create table posting_fact (
                  posting_id text primary key,
                  effective_date text not null,
                  recorded_at text not null,
                  actor_id text not null,
                  actor_type text not null,
                  command_id text not null,
                  idempotency_key text not null unique,
                  causation_id text not null,
                  correlation_id text null,
                  reason text null,
                  source_channel text not null,
                  prior_posting_id text null
              )
              """);
          database.executeStatement(
              """
              insert into posting_fact (
                  posting_id,
                  effective_date,
                  recorded_at,
                  actor_id,
                  actor_type,
                  command_id,
                  idempotency_key,
                  causation_id,
                  correlation_id,
                  reason,
                  source_channel,
                  prior_posting_id
              ) values (
                  'posting-partial',
                  '2026-04-07',
                  '2026-04-07T10:15:30Z',
                  'actor-1',
                  'AGENT',
                  'command-partial',
                  'idem-partial',
                  'cause-1',
                  null,
                  null,
                  'CLI',
                  null
              )
              """);
        });
  }

  private static void createEmptySqliteFile(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), database -> {});
  }

  private static void createSchemaOnlyBook(Path bookPath) {
    withStandaloneDatabase(staticBookAccess(bookPath), SqliteBookSchemaBootstrap::initializeBook);
  }

  private static void createPartialFinGrindBook(
      Path bookPath,
      boolean includeBookMeta,
      boolean includeAccount,
      boolean includePostingFact,
      boolean includeJournalLine,
      boolean includeInitializedMarker) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          database.executeStatement("pragma application_id = " + SqliteBookContract.APPLICATION_ID);
          database.executeStatement("pragma user_version = " + SqliteBookContract.FORMAT_VERSION);
          if (includeBookMeta) {
            database.executeStatement(
                "create table book_meta (key text primary key, value text not null)");
          }
          if (includeAccount) {
            database.executeStatement(
                """
                create table account (
                    account_code text primary key,
                    account_name text not null,
                    normal_balance text not null,
                    active integer not null,
                    declared_at text not null
                )
                """);
          }
          if (includePostingFact) {
            database.executeStatement(
                """
                create table posting_fact (
                    posting_id text primary key,
                    effective_date text not null,
                    recorded_at text not null,
                    actor_id text not null,
                    actor_type text not null,
                    command_id text not null,
                    idempotency_key text not null unique,
                    causation_id text not null,
                    correlation_id text null,
                    reason text null,
                    source_channel text not null,
                    prior_posting_id text null
                )
                """);
          }
          if (includeJournalLine) {
            database.executeStatement(
                """
                create table journal_line (
                    posting_id text not null,
                    line_order integer not null,
                    account_code text not null,
                    entry_side text not null,
                    currency_code text not null,
                    amount text not null
                )
                """);
          }
          if (includeInitializedMarker) {
            insertInitializedAtRow(database);
          }
        });
  }

  private static void initializeBookOnDisk(Path bookPath) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database -> {
          SqliteBookSchemaBootstrap.initializeBook(database);
          insertInitializedAtRow(database);
          insertAccountRow(database, "1000", "Cash", "DEBIT", 1, "2026-04-07T10:15:30Z");
          insertAccountRow(database, "2000", "Revenue", "CREDIT", 1, "2026-04-07T10:15:30Z");
        });
  }

  private static void deactivateAccount(Path bookPath, String accountCode) {
    withStandaloneDatabase(
        staticBookAccess(bookPath),
        database ->
            database.executeStatement(
                """
                update account
                set active = 0
                where account_code = '%s'
                """
                    .formatted(accountCode)));
  }

  private static void setStoreDatabase(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteNativeDatabase database) {
    SqliteStoreTestAccess.publishNativeDatabase(postingFactStore, database);
  }

  private static void setStoreBookPassphrase(
      SqlitePostingFactStore postingFactStore, SqliteBookPassphrase bookPassphrase) {
    SqliteStoreTestAccess.setPendingPassphrase(postingFactStore, bookPassphrase);
  }

  private static void setStoreCachedBookState(
      SqlitePostingFactStore postingFactStore,
      @org.jspecify.annotations.Nullable SqliteBookStateSnapshot cachedBookState) {
    SqliteStoreTestAccess.setCachedState(postingFactStore, cachedBookState);
  }

  private static boolean storeBooleanField(
      SqlitePostingFactStore postingFactStore, String fieldName) {
    return switch (fieldName) {
      case "closed" -> SqliteStoreTestAccess.closed(postingFactStore);
      case "ledgerPlanTransactionActive" ->
          SqliteStoreTestAccess.ledgerPlanTransactionActive(postingFactStore);
      case "ledgerPlanTransactionBegunInDatabase" ->
          SqliteStoreTestAccess.ledgerPlanTransactionBegunInDatabase(postingFactStore);
      default ->
          throw new IllegalArgumentException("Unsupported store boolean field: " + fieldName);
    };
  }

  private static SqliteNativeDatabase storeDatabase(SqlitePostingFactStore postingFactStore) {
    return SqliteStoreTestAccess.currentDatabaseHandle(postingFactStore);
  }

  private static SqliteNativeDatabase requireStoreDatabase(
      SqlitePostingFactStore postingFactStore) {
    SqliteNativeDatabase database = storeDatabase(postingFactStore);
    assertNotNull(database);
    return database;
  }

  private static void closeStoreDatabase(SqlitePostingFactStore postingFactStore) {
    requireStoreDatabase(postingFactStore).close();
  }

  private static byte[] passphraseBytes(SqliteBookPassphrase passphrase) {
    return passphrase.utf8BytesCopy();
  }

  private static MethodHandle constantMethodHandle(Object value, Class<?>... parameterTypes) {
    return MethodHandles.dropArguments(
        MethodHandles.constant(constantType(value), value), 0, parameterTypes);
  }

  private static Class<?> constantType(Object value) {
    return switch (value) {
      case Integer _ -> int.class;
      case Long _ -> long.class;
      case MemorySegment _ -> MemorySegment.class;
      default -> value.getClass();
    };
  }

  private void assertOpenConfigurationFailure(String driftSql, String expectedMessage) {
    Path bookPath =
        tempDirectory.resolve(expectedMessage.replace(' ', '-').replace('.', '_') + ".sqlite");
    try (SqliteNativeDatabase database =
        SqliteConnectionConfigurer.configureOpenedDatabase(
            SqliteNativeLibrary.open(bookAccess(bookPath)),
            SqliteStoreAccessMode.READ_WRITE_CREATE)) {
      database.executeScript(driftSql + ";");

      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () ->
                  SqliteConnectionConfigurer.assertOpenConfiguration(
                      database, SqliteStoreAccessMode.READ_WRITE_CREATE));

      assertEquals(expectedMessage, exception.getMessage());
    }
  }

  private static void withStandaloneDatabase(BookAccess bookAccess, SqliteDatabaseAction action) {
    try (SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess)) {
      action.run(database);
    }
  }

  private static <T> T withStandaloneDatabaseResult(
      BookAccess bookAccess, SqliteDatabaseQuery<T> query) {
    try (SqliteNativeDatabase database = SqliteNativeLibrary.open(bookAccess)) {
      return query.run(database);
    }
  }

  private BookAccess bookAccess(Path bookPath) {
    try {
      Path keyDirectory = tempDirectory.resolve("book-keys");
      Files.createDirectories(keyDirectory);
      Path keyPath = keyDirectory.resolve(bookPath.getFileName() + ".key");
      if (keyPath.getParent() != null) {
        Files.createDirectories(keyPath.getParent());
      }
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static BookAccess staticBookAccess(Path bookPath) {
    try {
      Path keyDirectory = Files.createTempDirectory("fingrind-book-key-");
      Path keyPath = keyDirectory.resolve("book.key");
      keyDirectory.toFile().deleteOnExit();
      keyPath.toFile().deleteOnExit();
      writeSecureKeyFile(keyPath, TEST_BOOK_KEY);
      return new BookAccess(bookPath, new BookAccess.PassphraseSource.KeyFile(keyPath));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void assertInvalidPlaintextBookFailure(IllegalStateException exception) {
    assertTrue(
        exception
            .getMessage()
            .contains(
                "FinGrind could not authenticate the selected protected book with the supplied passphrase source."));
    assertTrue(
        exception
            .getMessage()
            .contains(
                "FinGrind could not authenticate the selected protected book with the supplied passphrase source."));
    assertFalse(exception.getMessage().contains("SQLITE_NOTADB"));
  }

  private static PostingCommitResult rejected(PostingRejection rejection) {
    return new PostingCommitResult.Rejected(rejection);
  }

  private static PostingDraft postingDraft(
      String postingId,
      String idempotencyKey,
      Optional<ReversalReference> reversalReference,
      Optional<ReversalReason> reason) {
    PostingFact postingFact = postingFact(postingId, idempotencyKey, reversalReference, reason);
    return new PostingDraft(
        postingFact.journalEntry(), postingFact.postingLineage(), postingFact.provenance());
  }

  private static PostingRejection.AccountStateViolations accountStateViolations(
      PostingRejection.AccountStateViolation... violations) {
    return new PostingRejection.AccountStateViolations(List.of(violations));
  }

  private static ListAccountsQuery firstAccountPage() {
    return new ListAccountsQuery(50, 0);
  }

  @SafeVarargs
  private static void assertInitializedQueryViewFailure(ThrowingRunnable... invocations) {
    for (ThrowingRunnable invocation : invocations) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, invocation::run);
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
    }
  }

  @SafeVarargs
  private static void assertWrappedQueryViewNativeFailure(ThrowingRunnable... invocations) {
    for (ThrowingRunnable invocation : invocations) {
      IllegalStateException exception = assertThrows(IllegalStateException.class, invocation::run);
      assertTrue(exception.getMessage().contains("Failed to query SQLite book."));
      assertTrue(exception.getMessage().contains("SQLITE_CANTOPEN"));
    }
  }

  private static List<DeclaredAccount> listAccounts(SqlitePostingFactStore postingFactStore) {
    return postingFactStore.listAccounts(firstAccountPage()).accounts();
  }

  private static void writeSecureKeyFile(Path keyPath, String keyText) throws IOException {
    if (Files.notExists(keyPath)) {
      SqliteBookKeyFileGenerator.generate(keyPath);
    } else {
      SqliteBookKeyFileSecurity.requireSecureKeyFile(keyPath);
    }
    Files.writeString(keyPath, keyText, StandardCharsets.UTF_8);
  }

  /** Checked action against a temporary native SQLite handle. */
  @FunctionalInterface
  private interface SqliteDatabaseAction {
    void run(SqliteNativeDatabase database);
  }

  /** Checked query against a temporary native SQLite handle. */
  @FunctionalInterface
  private interface SqliteDatabaseQuery<T> {
    T run(SqliteNativeDatabase database);
  }

  /** Snapshot of one probed SQLite book-state helper result set. */
  private record BookStateProbe(
      boolean hasCanonicalTables, boolean hasInitializedMarker, String bookState) {}

  /** Assertion helper call that may surface reflective or native checked failures. */
  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws ReflectiveOperationException;
  }
}
