package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedCountingBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.localReadService;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.readService;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountPage;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.bookIdentity;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.financialPositionTaxonomy;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.foundPosting;
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.postingPage;
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateMissing;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingLookupOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests covering account and posting queries in {@link BookReadService}. */
class BookReadServiceAccountQueryTest {
  @Test
  void constructor_rejectsNullBookStore() {
    assertEquals(
        "bookStore",
        assertThrows(NullPointerException.class, () -> new BookReadService(nullOf())).getMessage());
  }

  @Test
  void inspectBook_delegatesToSessionInspection() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      BookInspection inspection =
          new BookInspection.Initialized(
              BookFormatContract.APPLICATION_ID,
              BookFormatContract.FORMAT_VERSION,
              BookFormatContract.FORMAT_VERSION,
              BookReadServiceTestSupport.FIXED_INSTANT,
              bookIdentity(),
              new BookInspection.CloseReadiness(
                  missingCloseTarget(FinancialPositionLineClassification.RESULT_HOLDING, List.of()),
                  missingCloseTarget(
                      FinancialPositionLineClassification.RETAINED_ACCUMULATED, List.of())));
      assertEquals(inspection, service.inspectBook());
    }
  }

  @Test
  void inspectBook_projectsNonInitializedInspectionWithoutCloseReadiness() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new BookInspection.Missing(BookFormatContract.FORMAT_VERSION), service.inspectBook());
    }
  }

  @Test
  void inspectBook_reportsAcceptedInterimCloseTargetWhenOneResultHoldingAccountIsActive() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("3200"),
          new AccountName("Retained Earnings"),
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);

      BookReadService service = readService(bookSession);
      assertEquals(
          new BookInspection.CloseReadiness(
              readyCloseTarget(
                  FinancialPositionLineClassification.RESULT_HOLDING, new AccountCode("3200")),
              missingCloseTarget(
                  FinancialPositionLineClassification.RETAINED_ACCUMULATED, List.of())),
          ((BookInspection.Initialized) service.inspectBook()).closeReadiness());
    }
  }

  @Test
  void
      inspectBook_reportsAmbiguousInterimCloseTargetCandidatesWhenMultipleResultHoldingAccountsExist() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("3200"),
          new AccountName("Retained Earnings A"),
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);
      bookSession.declareAccount(
          new AccountCode("3210"),
          new AccountName("Retained Earnings B"),
          AccountType.EQUITY,
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);

      BookReadService service = readService(bookSession);
      assertEquals(
          new BookInspection.CloseReadiness(
              ambiguousCloseTarget(
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  List.of(new AccountCode("3200"), new AccountCode("3210"))),
              missingCloseTarget(
                  FinancialPositionLineClassification.RETAINED_ACCUMULATED, List.of())),
          ((BookInspection.Initialized) service.inspectBook()).closeReadiness());
    }
  }

  @Test
  void requireInitializedBookIdentity_rejectsMissingAndNonInitializedBooks() {
    assertEquals(
        "Book identity is unavailable because the book is missing.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookLifecycleInspection.requireInitializedBookIdentity(
                        new BookLifecycleInspection.Missing(12)))
            .getMessage());
    assertEquals(
        "Book identity is unavailable for non-initialized book status blank-sqlite.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookLifecycleInspection.requireInitializedBookIdentity(
                        new BookLifecycleInspection.Existing(
                            BookLifecycleInspection.Status.BLANK_SQLITE, 0, 0, 12)))
            .getMessage());
  }

  @Test
  void listAccounts_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);
      assertEquals(
          new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listAccounts(new ListAccountsQuery(50, Optional.empty())));
    }
  }

  @Test
  void listAccounts_returnsDeclaredAccountsWhenInitialized() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      BookReadService service = readService(bookSession);
      assertEquals(
          new ListAccountsResult.Listed(
              new ListAccountsQuery(50, Optional.empty()),
              accountPage(List.of(CASH_ACCOUNT, REVENUE_ACCOUNT), 50, Optional.empty())),
          service.listAccounts(new ListAccountsQuery(50, Optional.empty())));
    }
  }

  @Test
  void getPosting_rejectsUninitializedAndMissingPosting() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = readService(uninitializedBook);
      assertEquals(
          new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.getPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      assertEquals(
          new GetPostingResult.Rejected(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-1"))),
          service.getPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void listPostings_rejectsUnknownFilteredAccount() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      assertEquals(
          new ListPostingsResult.Rejected(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.of(new AccountCode("9999")), null, null, 20, Optional.empty())));
    }
  }

  @Test
  void listPostings_rejectsUninitializedBookAndListsCommittedPostings() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = readService(uninitializedBook);
      assertEquals(
          new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      var postingFact = postingFact("posting-1", "idem-1");
      var publishedPostingFact = BookkeepingPublishedLanguageTranslator.toPublished(postingFact);
      bookSession.commit(postingFact);
      BookReadService service = readService(bookSession);
      assertEquals(
          new ListPostingsResult.Listed(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty()),
              postingPage(
                  Optional.empty(),
                  dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
                  List.of(publishedPostingFact),
                  20,
                  Optional.empty())),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty())));
      assertEquals(
          new ListPostingsResult.Listed(
              new ListPostingsQuery(
                  Optional.of(CASH_ACCOUNT.accountCode()), null, null, 20, Optional.empty()),
              postingPage(
                  Optional.of(CASH_ACCOUNT.accountCode()),
                  dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
                  List.of(publishedPostingFact),
                  20,
                  Optional.empty())),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.of(CASH_ACCOUNT.accountCode()), null, null, 20, Optional.empty())));
    }
  }

  @Test
  void listPostings_projects_reversal_backlinks_for_the_current_page() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      CommittedPosting originalPosting = postingFact("posting-1", "idem-1");
      CommittedPosting reversalPosting = reversalPostingFact("posting-2", "idem-2", "posting-1");
      bookSession.commit(originalPosting);
      bookSession.commit(reversalPosting);
      BookReadService service = readService(bookSession);

      assertEquals(
          new ListPostingsResult.Listed(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty()),
              new PostingPage(
                  bookIdentity(),
                  Optional.empty(),
                  dev.erst.fingrind.core.EffectiveDateRange.unbounded(),
                  List.of(
                      BookkeepingPublishedLanguageTranslator.toPublished(reversalPosting),
                      BookkeepingPublishedLanguageTranslator.toPublished(originalPosting)),
                  20,
                  Optional.empty(),
                  Map.of(originalPosting.postingId(), reversalPosting.postingId()))),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty())));
    }
  }

  @Test
  void getPostingAndAccountBalance_returnCommittedSnapshots() {
    BookReadServiceTestSupport.CountingFindAccountBookSession bookSession =
        initializedCountingBook();
    declareDefaultAccounts(bookSession.delegate());
    var postingFact = postingFact("posting-1", "idem-1");
    bookSession.commit(postingFact);
    bookSession.resetFindAccountCalls();
    bookSession.resetLifecycleCounts();
    BookReadService service = readService(bookSession);
    assertEquals(
        foundPosting(BookkeepingPublishedLanguageTranslator.toPublished(postingFact)),
        service.getPosting(new PostingId("posting-1")));
    assertEquals(
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                bookIdentity(),
                CASH_ACCOUNT,
                Optional.empty(),
                Optional.empty(),
                PostingCoverage.ALL_POSTING_KINDS,
                List.of(EUR_DEBIT_BALANCE))),
        service.accountBalance(AccountBalanceQuery.unbounded(CASH_ACCOUNT.accountCode())));
    assertEquals(0, bookSession.findAccountCalls());
    assertEquals(0, bookSession.inspectBookCalls());
    assertEquals(2, bookSession.allowsInitializedWorkflowCalls());
    assertEquals(2, bookSession.requireInitializedBookIdentityCalls());
  }

  @Test
  void lookupOutcomes_preserveRejectionAbsenceAndPresenceDistinctly() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookkeepingReadService service = localReadService(uninitializedBook);
      assertEquals(
          new BookkeepingLookupOutcome.Rejected<
              dev.erst.fingrind.executor.bookkeeping.RegisteredAccount>(
              new BookkeepingQueryRejection.BookNotInitialized()),
          service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(
          new BookkeepingLookupOutcome.Rejected<
              dev.erst.fingrind.executor.bookkeeping.CommittedPosting>(
              new BookkeepingQueryRejection.BookNotInitialized()),
          service.findPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      var postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookkeepingReadService service = localReadService(bookSession);
      assertEquals(
          new BookkeepingLookupOutcome.Found<>(BookReadServiceTestSupport.REGISTERED_CASH_ACCOUNT),
          service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(
          new BookkeepingLookupOutcome.Missing<
              dev.erst.fingrind.executor.bookkeeping.RegisteredAccount>(),
          service.findAccount(new AccountCode("9999")));
      assertEquals(
          new BookkeepingLookupOutcome.Found<>(postingFact),
          service.findPosting(new PostingId("posting-1")));
      assertEquals(
          new BookkeepingLookupOutcome.Missing<
              dev.erst.fingrind.executor.bookkeeping.CommittedPosting>(),
          service.findPosting(new PostingId("posting-missing")));
    }
  }

  @Test
  void accountBalance_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = readService(uninitializedBook);
      assertEquals(
          new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountBalance(AccountBalanceQuery.unbounded(CASH_ACCOUNT.accountCode())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      assertEquals(
          new AccountBalanceResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountBalance(AccountBalanceQuery.unbounded(CASH_ACCOUNT.accountCode())));
    }
  }

  @Test
  void readMethods_rejectNullInputs() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      BookkeepingReadService localService = localReadService(bookSession);
      assertThrows(NullPointerException.class, () -> service.listAccounts(nullOf()));
      assertThrows(NullPointerException.class, () -> service.getPosting(nullOf()));
      assertThrows(NullPointerException.class, () -> localService.findAccount(nullOf()));
      assertThrows(NullPointerException.class, () -> localService.findPosting(nullOf()));
      assertThrows(NullPointerException.class, () -> service.listPostings(nullOf()));
      assertThrows(NullPointerException.class, () -> service.accountBalance(nullOf()));
      assertThrows(NullPointerException.class, () -> service.trialBalance(nullOf()));
      assertThrows(NullPointerException.class, () -> service.accountLedger(nullOf()));
      assertThrows(NullPointerException.class, () -> service.periodSummary(nullOf()));
    }
  }

  private static BookInspection.CloseTargetReadiness readyCloseTarget(
      FinancialPositionLineClassification classification, AccountCode accountCode) {
    return new BookInspection.CloseTargetReadiness(
        true, classification, accountCode, null, null, List.of());
  }

  private static BookInspection.CloseTargetReadiness missingCloseTarget(
      FinancialPositionLineClassification classification, List<AccountCode> inactiveCandidates) {
    BookAdministrationRejection rejection =
        new CloseTargetAccountCandidateMissing(classification, inactiveCandidates);
    return new BookInspection.CloseTargetReadiness(
        false,
        classification,
        null,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        inactiveCandidates);
  }

  private static BookInspection.CloseTargetReadiness ambiguousCloseTarget(
      FinancialPositionLineClassification classification, List<AccountCode> candidates) {
    BookAdministrationRejection rejection =
        new dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous(
            classification, candidates);
    return new BookInspection.CloseTargetReadiness(
        false,
        classification,
        null,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        candidates);
  }

  private static CommittedPosting reversalPostingFact(
      String postingId, String idempotencyKey, String priorPostingId) {
    return new CommittedPosting(
        new PostingId(postingId),
        PostingApplicationServiceTestSupport.reversalJournalEntry(),
        PostingLineageModel.reversal(
            new ReversalReference(new PostingId(priorPostingId)),
            new ReversalReason("operator reversal")),
        PostingKind.STANDARD,
        dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
        ExecutorAccountingTestSupport.accountingEvidence(idempotencyKey),
        PostingApplicationServiceTestSupport.committedProvenance(idempotencyKey));
  }
}
