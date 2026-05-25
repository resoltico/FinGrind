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
import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountRole;
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
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.runtime.BookFormatContract;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingCoverage;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingLookupOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
import dev.erst.fingrind.executor.spi.BookLifecycleInspection;
import java.util.List;
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
              new BookInspection.ResultTransferReadiness(
                  false,
                  FinancialPositionLineClassification.RESULT_HOLDING,
                  null,
                  "result-holding-account-candidate-missing",
                  "No active declared result-holding account satisfies required classification 'RESULT_HOLDING'.",
                  List.of()));
      assertEquals(inspection, service.inspectBook());
    }
  }

  @Test
  void inspectBook_projectsNonInitializedInspectionWithoutResultTransferReadiness() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);

      assertEquals(
          new BookInspection.Missing(BookFormatContract.FORMAT_VERSION), service.inspectBook());
    }
  }

  @Test
  void inspectBook_reportsAcceptedResultTransferReadinessWhenOneRetainedEarningsAccountIsActive() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("3200"),
          new AccountName("Retained Earnings"),
          AccountType.EQUITY,
          accountRole(AccountType.EQUITY, NormalBalance.CREDIT),
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);

      BookReadService service = readService(bookSession);
      assertEquals(
          new BookInspection.ResultTransferReadiness(
              true,
              FinancialPositionLineClassification.RESULT_HOLDING,
              new AccountCode("3200"),
              null,
              null,
              List.of()),
          ((BookInspection.Initialized) service.inspectBook()).resultTransferReadiness());
    }
  }

  @Test
  void
      inspectBook_reportsAmbiguousResultTransferReadinessCandidatesWhenMultipleRetainedEarningsAccountsExist() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      bookSession.declareAccount(
          new AccountCode("3200"),
          new AccountName("Retained Earnings A"),
          AccountType.EQUITY,
          accountRole(AccountType.EQUITY, NormalBalance.CREDIT),
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);
      bookSession.declareAccount(
          new AccountCode("3210"),
          new AccountName("Retained Earnings B"),
          AccountType.EQUITY,
          accountRole(AccountType.EQUITY, NormalBalance.CREDIT),
          financialPositionTaxonomy(FinancialPositionLineClassification.RESULT_HOLDING),
          BookReadServiceTestSupport.FIXED_INSTANT);

      BookReadService service = readService(bookSession);
      assertEquals(
          new BookInspection.ResultTransferReadiness(
              false,
              FinancialPositionLineClassification.RESULT_HOLDING,
              null,
              "result-holding-account-candidate-ambiguous",
              "More than one active declared result-holding account satisfies required classification 'RESULT_HOLDING': 3200, 3210.",
              List.of(new AccountCode("3200"), new AccountCode("3210"))),
          ((BookInspection.Initialized) service.inspectBook()).resultTransferReadiness());
    }
  }

  @Test
  void requireInitializedBookIdentity_rejectsMissingAndNonInitializedBooks() {
    assertEquals(
        "Book identity is unavailable because the book is missing.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookReadService.requireInitializedBookIdentity(
                        new BookLifecycleInspection.Missing(12)))
            .getMessage());
    assertEquals(
        "Book identity is unavailable for non-initialized book status blank-sqlite.",
        assertThrows(
                IllegalStateException.class,
                () ->
                    BookReadService.requireInitializedBookIdentity(
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
  void getPostingAndAccountBalance_returnCommittedSnapshots() {
    BookReadServiceTestSupport.CountingFindAccountBookSession bookSession =
        initializedCountingBook();
    declareDefaultAccounts(bookSession.delegate());
    var postingFact = postingFact("posting-1", "idem-1");
    bookSession.commit(postingFact);
    bookSession.resetFindAccountCalls();
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
}
