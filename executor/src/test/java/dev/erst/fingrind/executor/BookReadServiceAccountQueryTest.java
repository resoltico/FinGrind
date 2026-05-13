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
import static dev.erst.fingrind.executor.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.AccountBalanceQuery;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult;
import dev.erst.fingrind.contract.bookkeeping.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.bookkeeping.AccountPage;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.GetPostingResult;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListAccountsResult;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery;
import dev.erst.fingrind.contract.bookkeeping.ListPostingsResult;
import dev.erst.fingrind.contract.bookkeeping.PostingPage;
import dev.erst.fingrind.contract.runtime.BookInspection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingQueryRejection;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingLookupOutcome;
import dev.erst.fingrind.executor.bookkeeping.read.BookkeepingReadService;
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
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = readService(bookSession);
      BookInspection inspection =
          BookInspectionPublishedLanguageTranslator.toPublished(bookSession.inspectBook());
      assertEquals(inspection, service.inspectBook());
    }
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
              new AccountPage(List.of(CASH_ACCOUNT, REVENUE_ACCOUNT), 50, Optional.empty())),
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
              new PostingPage(List.of(publishedPostingFact), 20, Optional.empty())),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), null, null, 20, Optional.empty())));
      assertEquals(
          new ListPostingsResult.Listed(
              new PostingPage(List.of(publishedPostingFact), 20, Optional.empty())),
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
        new GetPostingResult.Found(BookkeepingPublishedLanguageTranslator.toPublished(postingFact)),
        service.getPosting(new PostingId("posting-1")));
    assertEquals(
        new AccountBalanceResult.Reported(
            new AccountBalanceSnapshot(
                CASH_ACCOUNT, Optional.empty(), Optional.empty(), List.of(EUR_DEBIT_BALANCE))),
        service.accountBalance(new AccountBalanceQuery(CASH_ACCOUNT.accountCode(), null, null)));
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
          service.accountBalance(new AccountBalanceQuery(CASH_ACCOUNT.accountCode(), null, null)));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = readService(bookSession);
      assertEquals(
          new AccountBalanceResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountBalance(new AccountBalanceQuery(CASH_ACCOUNT.accountCode(), null, null)));
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
