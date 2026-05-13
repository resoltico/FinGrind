package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.spi.AtomicBookStore;
import dev.erst.fingrind.executor.spi.BookStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit and integration tests for {@link SqlitePostingFactStore}. */
class SqliteBookSessionViewTest extends SqlitePostingFactStoreTestSupport {
  @Test
  void findByIdempotency_requiresInitializedBookWhenPostingIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findExistingPosting(new IdempotencyKey("missing-idem")));
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findByPostingId_requiresInitializedBookWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-posting.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findPosting(new PostingId("posting-1")));
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void findReversalFor_requiresInitializedBookWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-reversal.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException exception =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          "The selected SQLite file is not initialized as a FinGrind book.",
          exception.getMessage());
      assertFalse(Files.exists(databasePath));
    }
  }

  @Test
  void commit_returnsBookNotInitializedWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("books").resolve("missing-commit.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          rejected(new BookkeepingPostingRejection.BookNotInitialized()),
          commitPosting(
              postingFactStore,
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
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookContainsSchema()),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z")));
    }
  }

  @Test
  void seamAccessors_returnStoreAsEachNarrowSessionView() {
    Path databasePath = tempDirectory.resolve("seam-accessors.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertNotNull((BookStore) postingFactStore);
      assertNotNull((PostingValidationStore) postingFactStore);
      assertNotNull((AtomicBookStore) postingFactStore);
      assertSame(postingFactStore, (BookStore) postingFactStore);
      assertSame(postingFactStore, (PostingValidationStore) postingFactStore);
      assertSame(postingFactStore, (AtomicBookStore) postingFactStore);
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
            assertTrue(Files.exists(databasePath));
            assertFalse(postingFactStore.inspectBook().initialized());
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
                ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
                failure.code());
      }
    }
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void administrationView_delegatesMutationsWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("administration-view.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      assertEquals(
          new BookOpeningOutcome.Opened(Instant.parse("2026-04-07T10:15:30Z")),
          ((BookStore) postingFactStore).openBook(Instant.parse("2026-04-07T10:15:30Z")));
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          ((BookStore) postingFactStore)
              .declareAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  accountRole(dev.erst.fingrind.core.AccountType.ASSET, NormalBalance.DEBIT),
                  Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.inspectBook().initialized());
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
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertTrue(((BookStore) postingFactStore).inspectBook().initialized());
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          ((BookStore) postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))),
          ((BookStore) postingFactStore)
              .findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertEquals(
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          ((BookStore) postingFactStore).findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          ((BookStore) postingFactStore).findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.findReversalFor(new PostingId("posting-1")),
          ((BookStore) postingFactStore).findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())),
          ((BookStore) postingFactStore)
              .commit(
                  postingDraft("posting-2", "idem-2", Optional.empty(), Optional.empty()),
                  () -> new PostingId("posting-2")));
      assertTrue(postingFactStore.inspectBook().initialized());
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
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      PostingHistoryQuery postingsQuery =
          new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty());
      AccountBalanceCriteria balanceQuery =
          new AccountBalanceCriteria(new AccountCode("1000"), null, null);
      assertEquals(postingFactStore.inspectBook(), ((BookStore) postingFactStore).inspectBook());
      assertTrue(((BookStore) postingFactStore).inspectBook().initialized());
      assertEquals(
          postingFactStore.listAccounts(firstAccountPage()),
          ((BookStore) postingFactStore).listAccounts(firstAccountPage()));
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          ((BookStore) postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          ((BookStore) postingFactStore).findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.listPostings(postingsQuery),
          ((BookStore) postingFactStore).listPostings(postingsQuery));
      assertEquals(
          postingFactStore.accountBalance(balanceQuery),
          ((BookStore) postingFactStore).accountBalance(balanceQuery));
      assertEquals(postingFactStore.inspectBook(), ((BookStore) postingFactStore).inspectBook());
    }
  }

  @Test
  void reportView_delegatesReportsWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("report-view.sqlite");
    CommittedPosting openingPosting =
        postingFact(
            "posting-1",
            "idem-1",
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
    CommittedPosting zeroingPosting =
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
      commitPosting(postingFactStore, openingPosting);
      commitPosting(postingFactStore, zeroingPosting);
      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      RegisteredAccount cashAccount =
          postingFactStore.findAccount(new AccountCode("1000")).orElseThrow();
      assertTrue(postingFactStore.inspectBook().initialized());
      assertEquals(
          Optional.of(cashAccount),
          ((BookStore) postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.trialBalance(new TrialBalanceCriteria(Optional.empty())),
          ((BookStore) postingFactStore).trialBalance(new TrialBalanceCriteria(Optional.empty())));
      assertEquals(
          new AccountLedgerReport(
              publishedAccount(revenueAccount),
              EffectiveDateRange.unbounded(),
              List.of(),
              List.of(
                  new AccountLedgerEntry(
                      publishedPostingFact(openingPosting),
                      balance("EUR", "0.00", "10.00", "10.00", BalanceSide.CREDIT),
                      money("EUR", "10.00"),
                      BalanceSide.CREDIT),
                  new AccountLedgerEntry(
                      publishedPostingFact(zeroingPosting),
                      balance("EUR", "10.00", "0.00", "10.00", BalanceSide.DEBIT),
                      money("EUR", "0.00"),
                      BalanceSide.ZERO)),
              List.of(balance("EUR", "10.00", "10.00", "0.00", BalanceSide.ZERO))),
          published(
              ((BookStore) postingFactStore)
                  .accountLedger(
                      new AccountLedgerCriteria(
                          new AccountCode("2000"), EffectiveDateRange.unbounded()),
                      revenueAccount)));
      assertEquals(
          postingFactStore.periodSummary(
              new PeriodSummaryCriteria(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))),
          ((BookStore) postingFactStore)
              .periodSummary(
                  new PeriodSummaryCriteria(
                      LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))));
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void reportMethods_throwBookNotInitializedWhenBookIsMissing() {
    Path databasePath = tempDirectory.resolve("missing-report-book.sqlite");
    RegisteredAccount cashAccount =
        registeredAccount(
            new AccountCode("1000"),
            new AccountName("Cash"),
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T10:15:30Z"));
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(new TrialBalanceCriteria(Optional.empty())));
      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      new AccountLedgerCriteria(new AccountCode("1000"), null, null), cashAccount));
      IllegalStateException periodSummaryFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.periodSummary(
                      new PeriodSummaryCriteria(
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
    PostingHistoryQuery postingsQuery =
        new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty());
    AccountBalanceCriteria balanceQuery =
        new AccountBalanceCriteria(new AccountCode("1000"), null, null);
    Path missingBookPath = tempDirectory.resolve("query-view-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(missingBookPath))) {
      assertFalse(postingFactStore.inspectBook().initialized());
      assertInitializedQueryViewFailure(
          () -> ((BookStore) postingFactStore).listAccounts(firstAccountPage()),
          () -> ((BookStore) postingFactStore).findAccount(new AccountCode("1000")),
          () -> ((BookStore) postingFactStore).findPosting(new PostingId("posting-1")),
          () -> ((BookStore) postingFactStore).listPostings(postingsQuery),
          () -> ((BookStore) postingFactStore).accountBalance(balanceQuery));
    }
    Path rawSqlitePath = tempDirectory.resolve("query-view-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(rawSqlitePath))) {
      assertFalse(postingFactStore.inspectBook().initialized());
      assertInitializedQueryViewFailure(
          () -> ((BookStore) postingFactStore).listAccounts(firstAccountPage()),
          () -> ((BookStore) postingFactStore).findAccount(new AccountCode("1000")),
          () -> ((BookStore) postingFactStore).findPosting(new PostingId("posting-1")),
          () -> ((BookStore) postingFactStore).listPostings(postingsQuery),
          () -> ((BookStore) postingFactStore).accountBalance(balanceQuery));
    }
  }

  @Test
  void queryView_wrapsNativeFailuresFromDirectQueryCalls() throws Exception {
    Path databasePath = tempDirectory.resolve("query-view-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore =
        new SqlitePostingFactStore(bookAccess(databasePath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath));
      assertWrappedQueryViewNativeFailure(
          () -> ((BookStore) postingFactStore).listAccounts(firstAccountPage()),
          () -> ((BookStore) postingFactStore).findAccount(new AccountCode("1000")),
          () -> ((BookStore) postingFactStore).findPosting(new PostingId("posting-1")),
          () ->
              ((BookStore) postingFactStore)
                  .listPostings(
                      new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty())),
          () ->
              ((BookStore) postingFactStore)
                  .accountBalance(new AccountBalanceCriteria(new AccountCode("1000"), null, null)));
      setStoreDatabase(postingFactStore, null);
    }
  }

  private static CurrencyBalance balance(
      String currencyCode,
      String debitTotal,
      String creditTotal,
      String netAmount,
      BalanceSide balanceSide) {
    CurrencyBalance balance =
        CurrencyBalance.ofTotals(money(currencyCode, debitTotal), money(currencyCode, creditTotal));
    if (!balance.netAmount().equals(money(currencyCode, netAmount))
        || balance.balanceSide() != balanceSide) {
      throw new IllegalArgumentException("Test fixture balance does not match derived totals.");
    }
    return balance;
  }
}
