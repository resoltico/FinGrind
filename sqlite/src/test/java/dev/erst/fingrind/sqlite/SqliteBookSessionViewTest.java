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
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PeriodCloseStore;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          new BookOpeningOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookContainsSchema()),
          postingFactStore.openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
    }
  }

  @Test
  void seamAccessors_returnStoreAsEachNarrowSessionView() {
    Path databasePath = tempDirectory.resolve("seam-accessors.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertNotNull((SqliteAdministrationSession) postingFactStore);
      assertNotNull((SqliteReadSession) postingFactStore);
      assertNotNull((SqlitePostingSession) postingFactStore);
      assertNotNull((SqlitePeriodCloseSession) postingFactStore);
      assertNotNull((SqlitePlanExecutionSession) postingFactStore);
      assertNotNull((SqliteRekeySession) postingFactStore);
      assertNotNull((BookAdministrationStore) postingFactStore);
      assertNotNull((BookkeepingReadStore) postingFactStore);
      assertNotNull((PostingValidationStore) postingFactStore);
      assertNotNull((PostingCommitStore) postingFactStore);
      assertNotNull((PeriodCloseStore) postingFactStore);
      assertNotNull((LedgerPlanTransaction) postingFactStore);
      assertSame(postingFactStore, (SqliteAdministrationSession) postingFactStore);
      assertSame(postingFactStore, (SqliteReadSession) postingFactStore);
      assertSame(postingFactStore, (SqlitePostingSession) postingFactStore);
      assertSame(postingFactStore, (SqlitePeriodCloseSession) postingFactStore);
      assertSame(postingFactStore, (SqlitePlanExecutionSession) postingFactStore);
      assertSame(postingFactStore, (SqliteRekeySession) postingFactStore);
      assertSame(postingFactStore, (BookAdministrationStore) postingFactStore);
      assertSame(postingFactStore, (BookkeepingReadStore) postingFactStore);
      assertSame(postingFactStore, (PostingValidationStore) postingFactStore);
      assertSame(postingFactStore, (PostingCommitStore) postingFactStore);
      assertSame(postingFactStore, (PeriodCloseStore) postingFactStore);
      assertSame(postingFactStore, (LedgerPlanTransaction) postingFactStore);
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void administrationView_delegatesMutationsWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("administration-view.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      assertEquals(
          openedBook(Instant.parse("2026-04-07T10:15:30Z")),
          administrationView(postingFactStore)
              .openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity()));
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              registeredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  NormalBalance.DEBIT,
                  true,
                  Instant.parse("2026-04-07T10:15:30Z"))),
          administrationView(postingFactStore)
              .declareAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  dev.erst.fingrind.core.AccountType.ASSET,
                  accountRole(dev.erst.fingrind.core.AccountType.ASSET, NormalBalance.DEBIT),
                  accountTaxonomy(dev.erst.fingrind.core.AccountType.ASSET),
                  Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void postingView_delegatesReadsWritesWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("posting-view.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithDefaultAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())),
          commitPosting(
              postingFactStore,
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty())));
      assertTrue(validationView(postingFactStore).inspectBook().initialized());
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          validationView(postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))),
          validationView(postingFactStore)
              .findAccounts(Set.of(new AccountCode("1000"), new AccountCode("2000"))));
      assertEquals(
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          validationView(postingFactStore).findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          validationView(postingFactStore).findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.findReversalFor(new PostingId("posting-1")),
          validationView(postingFactStore).findReversalFor(new PostingId("posting-1")));
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty())),
          commitView(postingFactStore)
              .commit(
                  postingDraft("posting-2", "idem-2", Optional.empty(), Optional.empty()),
                  () -> new PostingId("posting-2")));
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void queryView_delegatesQueriesWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("query-view.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
          AccountBalanceCriteria.unbounded(new AccountCode("1000"));
      assertEquals(postingFactStore.inspectBook(), readView(postingFactStore).inspectBook());
      assertTrue(readView(postingFactStore).inspectBook().initialized());
      assertEquals(
          postingFactStore.listAccounts(firstAccountPage()),
          readView(postingFactStore).listAccounts(firstAccountPage()));
      assertEquals(
          postingFactStore.findAccount(new AccountCode("1000")),
          readView(postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.findPosting(new PostingId("posting-1")),
          readView(postingFactStore).findPosting(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.listPostings(postingsQuery),
          readView(postingFactStore).listPostings(postingsQuery));
      assertEquals(
          postingFactStore.accountBalance(balanceQuery),
          readView(postingFactStore).accountBalance(balanceQuery));
      assertEquals(postingFactStore.inspectBook(), readView(postingFactStore).inspectBook());
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
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
          readView(postingFactStore).findAccount(new AccountCode("1000")));
      assertEquals(
          postingFactStore.trialBalance(trialBalanceCriteria(Optional.empty())),
          readView(postingFactStore).trialBalance(trialBalanceCriteria(Optional.empty())));
      assertEquals(
          new AccountLedgerReport(
              bookIdentity(),
              publishedAccount(revenueAccount),
              EffectiveDateRange.unbounded(),
              dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS,
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
              readView(postingFactStore)
                  .accountLedger(
                      new AccountLedgerCriteria(
                          new AccountCode("2000"),
                          EffectiveDateRange.unbounded(),
                          dev.erst.fingrind.core.PostingCoverage.ALL_POSTING_KINDS),
                      revenueAccount)));
      assertEquals(
          postingFactStore.periodSummary(
              new PeriodSummaryCriteria(
                  LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-08"))),
          readView(postingFactStore)
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
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      IllegalStateException trialBalanceFailure =
          assertThrows(
              IllegalStateException.class,
              () -> postingFactStore.trialBalance(trialBalanceCriteria(Optional.empty())));
      IllegalStateException accountLedgerFailure =
          assertThrows(
              IllegalStateException.class,
              () ->
                  postingFactStore.accountLedger(
                      AccountLedgerCriteria.unbounded(new AccountCode("1000")), cashAccount));
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
    AccountBalanceCriteria balanceQuery = AccountBalanceCriteria.unbounded(new AccountCode("1000"));
    Path missingBookPath = tempDirectory.resolve("query-view-missing.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(missingBookPath))) {
      assertFalse(postingFactStore.inspectBook().initialized());
      assertInitializedQueryViewFailure(
          () -> readView(postingFactStore).listAccounts(firstAccountPage()),
          () -> readView(postingFactStore).findAccount(new AccountCode("1000")),
          () -> readView(postingFactStore).findPosting(new PostingId("posting-1")),
          () -> readView(postingFactStore).listPostings(postingsQuery),
          () -> readView(postingFactStore).accountBalance(balanceQuery));
    }
    Path rawSqlitePath = tempDirectory.resolve("query-view-uninitialized.sqlite");
    createEmptySqliteFile(rawSqlitePath);
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(rawSqlitePath))) {
      assertFalse(postingFactStore.inspectBook().initialized());
      assertInitializedQueryViewFailure(
          () -> readView(postingFactStore).listAccounts(firstAccountPage()),
          () -> readView(postingFactStore).findAccount(new AccountCode("1000")),
          () -> readView(postingFactStore).findPosting(new PostingId("posting-1")),
          () -> readView(postingFactStore).listPostings(postingsQuery),
          () -> readView(postingFactStore).accountBalance(balanceQuery));
    }
  }

  @Test
  void queryView_wrapsNativeFailuresFromDirectQueryCalls() throws Exception {
    Path databasePath = tempDirectory.resolve("query-view-native-failure.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      setStoreDatabase(postingFactStore, staleDatabaseHandle(databasePath));
      assertWrappedQueryViewNativeFailure(
          () -> readView(postingFactStore).listAccounts(firstAccountPage()),
          () -> readView(postingFactStore).findAccount(new AccountCode("1000")),
          () -> readView(postingFactStore).findPosting(new PostingId("posting-1")),
          () ->
              readView(postingFactStore)
                  .listPostings(
                      new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty())),
          () ->
              readView(postingFactStore)
                  .accountBalance(AccountBalanceCriteria.unbounded(new AccountCode("1000"))));
      setStoreDatabase(postingFactStore, null);
    }
  }

  private static BookAdministrationStore administrationView(
      SqlitePostingFactStore postingFactStore) {
    return postingFactStore;
  }

  private static PostingValidationStore validationView(SqlitePostingFactStore postingFactStore) {
    return postingFactStore;
  }

  private static PostingCommitStore commitView(SqlitePostingFactStore postingFactStore) {
    return postingFactStore;
  }

  private static BookkeepingReadStore readView(SqlitePostingFactStore postingFactStore) {
    return postingFactStore;
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
