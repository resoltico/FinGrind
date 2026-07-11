package dev.erst.fingrind.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.AccountLedgerEntry;
import dev.erst.fingrind.contract.bookkeeping.AccountLedgerReport;
import dev.erst.fingrind.contract.runtime.BookAccess;
import dev.erst.fingrind.contract.runtime.ContractDecision;
import dev.erst.fingrind.contract.runtime.ContractErrors;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.CurrencyBalance;
import dev.erst.fingrind.core.EffectiveDateRange;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReportingPeriod;
import dev.erst.fingrind.executor.InterimResultSweepService;
import dev.erst.fingrind.executor.bookkeeping.AccountBalanceCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountDeclarationOutcome;
import dev.erst.fingrind.executor.bookkeeping.AccountLedgerCriteria;
import dev.erst.fingrind.executor.bookkeeping.AccountRegistryQuery;
import dev.erst.fingrind.executor.bookkeeping.BookOpeningOutcome;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingAdministrationRejection;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepDraft;
import dev.erst.fingrind.executor.bookkeeping.InterimResultSweepOutcome;
import dev.erst.fingrind.executor.bookkeeping.PeriodSummaryCriteria;
import dev.erst.fingrind.executor.bookkeeping.PostingHistoryQuery;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import dev.erst.fingrind.executor.bookkeeping.TrialBalanceCriteria;
import dev.erst.fingrind.executor.spi.BookAdministrationStore;
import dev.erst.fingrind.executor.spi.BookkeepingReadStore;
import dev.erst.fingrind.executor.spi.LedgerPlanTransaction;
import dev.erst.fingrind.executor.spi.PostingCommitResult;
import dev.erst.fingrind.executor.spi.PostingCommitStore;
import dev.erst.fingrind.executor.spi.ReportingPeriodCloseStore;
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
          postingFactStore.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())));
    }
  }

  @Test
  void seamAccessors_returnStoreAsEachNarrowSessionView() {
    Path databasePath = tempDirectory.resolve("seam-accessors.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath));
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore);
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqlitePostingSession postingSession = SqliteCapabilitySessions.posting(postingFactStore);
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore);
        SqlitePlanExecutionSession planExecutionSession =
            SqliteCapabilitySessions.planExecution(postingFactStore);
        SqliteRekeySession rekeySession = SqliteCapabilitySessions.rekey(postingFactStore)) {
      assertNotNull(administrationSession);
      assertNotNull(readSession);
      assertNotNull(postingSession);
      assertNotNull(reportingPeriodCloseSession);
      assertNotNull(planExecutionSession);
      assertNotNull(rekeySession);
      assertNotNull((BookAdministrationStore) administrationSession);
      assertNotNull((BookkeepingReadStore) readSession);
      assertNotNull((PostingValidationStore) postingSession);
      assertNotNull((PostingCommitStore) postingSession);
      assertNotNull((ReportingPeriodCloseStore) reportingPeriodCloseSession);
      assertNotNull((LedgerPlanTransaction) planExecutionSession);
      assertNotSame(postingFactStore, administrationSession);
      assertNotSame(postingFactStore, readSession);
      assertNotSame(postingFactStore, postingSession);
      assertNotSame(postingFactStore, reportingPeriodCloseSession);
      assertNotSame(postingFactStore, planExecutionSession);
      assertNotSame(postingFactStore, rekeySession);
      assertEquals(postingFactStore.inspectBook(), administrationSession.inspectBook());
      assertEquals(postingFactStore.inspectBook(), readSession.inspectBook());
    }
  }

  @Test
  void
      reportingPeriodCloseSession_delegatesHighLevelAndLowLevelTransfersWithoutOwningStoreLifecycle() {
    LocalDate effectiveDate = LocalDate.parse("2026-04-07");
    Instant sweptAt = Instant.parse("2026-04-19T10:15:30Z");
    Path databasePath = tempDirectory.resolve("interim-result-sweep-session.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      AccountDeclarationOutcome.Declared declared =
          assertInstanceOf(
              AccountDeclarationOutcome.Declared.class,
              postingFactStore.declareAccount(
                  new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                      new AccountCode("3200"),
                      new AccountName("Retained Earnings"),
                      AccountType.EQUITY,
                      financialPositionTaxonomy(
                          FinancialPositionLineClassification.RESULT_HOLDING)),
                  sweptAt));
      assertEquals(new AccountCode("3200"), declared.account().accountCode());
      commitPosting(
          postingFactStore,
          postingFact(
              "posting-1",
              "idem-1",
              effectiveDate,
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "10.00"))));

      InterimResultSweepOutcome.Transferred transferred =
          assertInstanceOf(
              InterimResultSweepOutcome.Transferred.class,
              new InterimResultSweepService(
                      reportingPeriodCloseSession,
                      reportingPeriodCloseSession,
                      () -> new PostingId("interim-result-sweep-1"),
                      java.time.Clock.fixed(sweptAt, java.time.ZoneOffset.UTC))
                  .interimResultSweep(new ReportingPeriod(effectiveDate, effectiveDate)));

      assertEquals(1, transferred.sweptInterimResult().sweepOrder());
      assertEquals(
          Optional.of(effectiveDate),
          reportingPeriodCloseSession.transferredThroughEffectiveDate());
      assertEquals(2, reportingPeriodCloseSession.postings(EffectiveDateRange.unbounded()).size());
    }

    Path missingBookPath = tempDirectory.resolve("interim-result-sweep-session-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(missingStore)) {
      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          SqliteCapabilitySessions.storeOf(reportingPeriodCloseSession)
              .interimResultSweep(
                  emptyInterimResultSweepDraft(effectiveDate, sweptAt),
                  () -> new PostingId("unused")));
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
              .openBook(Instant.parse("2026-04-07T10:15:30Z"), bookIdentity(), List.of()));
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
                  new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      dev.erst.fingrind.core.AccountType.ASSET,
                      accountTaxonomy(
                          dev.erst.fingrind.core.AccountType.ASSET, NormalBalance.DEBIT)),
                  Instant.parse("2026-04-07T10:15:30Z")));
      assertTrue(postingFactStore.inspectBook().initialized());
    }
  }

  @Test
  void postingView_delegatesReadsWritesWithoutOwningStoreLifecycle() {
    Path databasePath = tempDirectory.resolve("posting-view.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath))) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()), false),
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
              postingFact("posting-2", "idem-2", Optional.empty(), Optional.empty()), false),
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
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      assertEquals(
          new PostingCommitResult.Committed(
              postingFact("posting-1", "idem-1", Optional.empty(), Optional.empty()), false),
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
      initializeBookWithMinimalNumericAccounts(postingFactStore);
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

  @Test
  void capabilityWrappers_coverRemainingForwardersAndRejectForeignSessionLookup() {
    Path databasePath = tempDirectory.resolve("capability-wrapper-coverage.sqlite");
    try (SqlitePostingFactStore postingFactStore = openStore(bookAccess(databasePath));
        SqliteAdministrationSession administrationSession =
            SqliteCapabilitySessions.administration(postingFactStore);
        SqliteReadSession readSession = SqliteCapabilitySessions.read(postingFactStore);
        SqlitePostingSession postingSession = SqliteCapabilitySessions.posting(postingFactStore);
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(postingFactStore);
        SqlitePlanExecutionSession planExecutionSession =
            SqliteCapabilitySessions.planExecution(postingFactStore)) {
      initializeBookWithMinimalNumericAccounts(postingFactStore);
      CommittedPosting openingPosting =
          postingFact(
              "posting-1",
              "idem-1",
              LocalDate.parse("2026-04-07"),
              Instant.parse("2026-04-07T10:15:30Z"),
              List.of(
                  line("1000", JournalLine.EntrySide.DEBIT, "EUR", "10.00"),
                  line("2000", JournalLine.EntrySide.CREDIT, "EUR", "10.00")));
      assertEquals(
          new PostingCommitResult.Committed(openingPosting, false),
          commitPosting(postingFactStore, openingPosting));
      AccountRegistryQuery accountPage = firstAccountPage();
      PostingHistoryQuery postingsQuery =
          new PostingHistoryQuery(Optional.empty(), null, null, 50, Optional.empty());
      EffectiveDateRange allDates = EffectiveDateRange.unbounded();
      TrialBalanceCriteria trialBalanceCriteria = trialBalanceCriteria(Optional.empty());
      RegisteredAccount revenueAccount =
          postingFactStore.findAccount(new AccountCode("2000")).orElseThrow();
      AccountBalanceCriteria cashBalanceCriteria =
          AccountBalanceCriteria.unbounded(new AccountCode("1000"));

      assertEquals(postingFactStore.allAccounts(), administrationSession.allAccounts());
      assertEquals(
          postingFactStore.listAccounts(accountPage),
          administrationSession.listAccounts(accountPage));
      assertEquals(postingFactStore.inspectBook(), administrationSession.inspectBook());
      assertEquals(postingFactStore.inspectBook(), reportingPeriodCloseSession.inspectBook());

      Set<AccountCode> accountCodes = Set.of(new AccountCode("1000"), new AccountCode("2000"));
      assertEquals(
          postingFactStore.findAccounts(accountCodes), readSession.findAccounts(accountCodes));
      assertEquals(postingFactStore.allAccounts(), readSession.allAccounts());
      assertEquals(
          postingFactStore.findExistingPosting(new IdempotencyKey("idem-1")),
          readSession.findExistingPosting(new IdempotencyKey("idem-1")));
      assertEquals(
          postingFactStore.findReversalFor(new PostingId("posting-1")),
          readSession.findReversalFor(new PostingId("posting-1")));
      assertEquals(
          postingFactStore.latestPostingEffectiveDate(), readSession.latestPostingEffectiveDate());
      assertEquals(
          postingFactStore.accountTotals(allDates, allPostingKinds()),
          readSession.accountTotals(allDates, allPostingKinds()));
      assertEquals(
          postingFactStore.trialBalance(trialBalanceCriteria),
          readSession.trialBalance(trialBalanceCriteria));
      assertEquals(
          postingFactStore.accountLedger(
              AccountLedgerCriteria.unbounded(new AccountCode("2000")), revenueAccount),
          readSession.accountLedger(
              AccountLedgerCriteria.unbounded(new AccountCode("2000")), revenueAccount));
      PeriodSummaryCriteria oneDaySummary =
          new PeriodSummaryCriteria(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07"));
      assertEquals(
          postingFactStore.periodSummary(oneDaySummary), readSession.periodSummary(oneDaySummary));

      assertEquals(postingFactStore.allAccounts(), postingSession.allAccounts());
      assertEquals(
          postingFactStore.listAccounts(accountPage), postingSession.listAccounts(accountPage));
      assertEquals(
          postingFactStore.accountBalance(cashBalanceCriteria),
          postingSession.accountBalance(cashBalanceCriteria));
      assertEquals(
          postingFactStore.listPostings(postingsQuery), postingSession.listPostings(postingsQuery));
      assertEquals(postingFactStore.postings(allDates), postingSession.postings(allDates));
      assertEquals(
          postingFactStore.earliestPostingEffectiveDate(),
          postingSession.earliestPostingEffectiveDate());
      assertEquals(
          postingFactStore.latestPostingEffectiveDate(),
          postingSession.latestPostingEffectiveDate());
      assertEquals(
          postingFactStore.transferredThroughEffectiveDate(),
          postingSession.transferredThroughEffectiveDate());
      assertEquals(
          postingFactStore.accountTotals(allDates, allPostingKinds()),
          postingSession.accountTotals(allDates, allPostingKinds()));
      assertEquals(
          postingFactStore.trialBalance(trialBalanceCriteria),
          postingSession.trialBalance(trialBalanceCriteria));
      assertEquals(
          postingFactStore.accountLedger(
              AccountLedgerCriteria.unbounded(new AccountCode("2000")), revenueAccount),
          postingSession.accountLedger(
              AccountLedgerCriteria.unbounded(new AccountCode("2000")), revenueAccount));
      assertEquals(
          postingFactStore.periodSummary(oneDaySummary),
          postingSession.periodSummary(oneDaySummary));
      assertEquals(
          postingFactStore.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())),
          postingSession.openBook(
              Instant.parse("2026-04-07T10:15:30Z"),
              bookIdentity(),
              dev.erst.fingrind.executor.bookkeeping.BookTemplateAccounts.declarations(
                  bookIdentity().bookDoctrine())));
      assertEquals(
          new AccountDeclarationOutcome.Declared(
              SqlitePostingFactFixtureSupport.registeredAccount(
                  new AccountCode("3000"),
                  new AccountName("Retained Earnings"),
                  dev.erst.fingrind.core.AccountType.EQUITY,
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:20:30Z"))),
          postingFactStore.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3000"),
                  new AccountName("Retained Earnings"),
                  dev.erst.fingrind.core.AccountType.EQUITY,
                  accountTaxonomy(dev.erst.fingrind.core.AccountType.EQUITY, NormalBalance.CREDIT)),
              Instant.parse("2026-04-07T10:20:30Z")));
      assertEquals(
          new AccountDeclarationOutcome.Unchanged(
              SqlitePostingFactFixtureSupport.registeredAccount(
                  new AccountCode("3000"),
                  new AccountName("Retained Earnings"),
                  dev.erst.fingrind.core.AccountType.EQUITY,
                  NormalBalance.CREDIT,
                  true,
                  Instant.parse("2026-04-07T10:20:30Z"))),
          postingSession.declareAccount(
              new dev.erst.fingrind.executor.bookkeeping.AccountDeclaration(
                  new AccountCode("3000"),
                  new AccountName("Retained Earnings"),
                  dev.erst.fingrind.core.AccountType.EQUITY,
                  accountTaxonomy(dev.erst.fingrind.core.AccountType.EQUITY, NormalBalance.CREDIT)),
              Instant.parse("2026-04-07T10:20:30Z")));

      assertEquals(postingFactStore.allAccounts(), reportingPeriodCloseSession.allAccounts());
      assertEquals(
          postingFactStore.listAccounts(accountPage),
          reportingPeriodCloseSession.listAccounts(accountPage));
      assertEquals(
          postingFactStore.postings(allDates), reportingPeriodCloseSession.postings(allDates));
      assertEquals(
          postingFactStore.earliestPostingEffectiveDate(),
          reportingPeriodCloseSession.earliestPostingEffectiveDate());
      assertEquals(
          postingFactStore.transferredThroughEffectiveDate(),
          reportingPeriodCloseSession.transferredThroughEffectiveDate());

      planExecutionSession.beginLedgerPlanTransaction();
      planExecutionSession.rollbackLedgerPlanTransaction();
      planExecutionSession.beginLedgerPlanTransaction();
      planExecutionSession.commitLedgerPlanTransaction();

      assertEquals(
          new InterimResultSweepOutcome.Rejected(
              new BookkeepingAdministrationRejection.BookNotInitialized()),
          interimResultSweepOnMissingBook());
      assertEquals(
          new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rejected(
              new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                  .BookNotInitialized()),
          rekeyOnMissingBook().requireAccepted());
    }

    assertThrows(
        NullPointerException.class,
        () -> SqliteCapabilitySessions.storeOf(NullTestSupport.nullOf(AutoCloseable.class)));
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> SqliteCapabilitySessions.storeOf((AutoCloseable) () -> {}));
    assertTrue(
        java.util.Objects.requireNonNull(exception.getMessage())
            .contains("owned SQLite store or capability wrapper"),
        exception::getMessage);
  }

  private InterimResultSweepOutcome interimResultSweepOnMissingBook() {
    Path missingBookPath = tempDirectory.resolve("capability-interim-result-sweep-missing.sqlite");
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath));
        SqliteReportingPeriodCloseSession reportingPeriodCloseSession =
            SqliteCapabilitySessions.reportingPeriodClose(missingStore)) {
      return new InterimResultSweepService(
              reportingPeriodCloseSession,
              reportingPeriodCloseSession,
              () -> new PostingId("unused"),
              java.time.Clock.fixed(
                  Instant.parse("2026-04-07T10:15:30Z"), java.time.ZoneOffset.UTC))
          .interimResultSweep(
              new ReportingPeriod(LocalDate.parse("2026-04-07"), LocalDate.parse("2026-04-07")));
    }
  }

  private ContractDecision<dev.erst.fingrind.contract.bookkeeping.RekeyBookResult>
      rekeyOnMissingBook() {
    Path missingBookPath = tempDirectory.resolve("capability-rekey-missing.sqlite");
    BookAccess.PassphraseSource replacementSource =
        BookAccess.PassphraseSource.StandardInput.INSTANCE;
    try (SqlitePostingFactStore missingStore = openStore(bookAccess(missingBookPath));
        SqliteRekeySession rekeySession = SqliteCapabilitySessions.rekey(missingStore)) {
      return rekeySession.rekeyBook(
          replacementSource,
          (resolvedBookPath, passphraseSource, intent) ->
              ContractDecision.accepted(
                  SqliteBookPassphrase.fromCharacters(
                      "capability-wrapper replacement", "rotated-key".toCharArray())),
          Instant.parse("2026-04-09T10:15:30Z"));
    }
  }

  private static BookAdministrationStore administrationView(
      SqlitePostingFactStore postingFactStore) {
    return SqliteCapabilitySessions.administration(postingFactStore);
  }

  private static PostingValidationStore validationView(SqlitePostingFactStore postingFactStore) {
    return SqliteCapabilitySessions.posting(postingFactStore);
  }

  private static PostingCommitStore commitView(SqlitePostingFactStore postingFactStore) {
    return SqliteCapabilitySessions.posting(postingFactStore);
  }

  private static BookkeepingReadStore readView(SqlitePostingFactStore postingFactStore) {
    return SqliteCapabilitySessions.read(postingFactStore);
  }

  private static InterimResultSweepDraft emptyInterimResultSweepDraft(
      LocalDate effectiveDate, Instant sweptAt) {
    return new InterimResultSweepDraft(
        new ReportingPeriod(effectiveDate, effectiveDate),
        new AccountCode("3200"),
        List.of(),
        sweptAt,
        List.of());
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
