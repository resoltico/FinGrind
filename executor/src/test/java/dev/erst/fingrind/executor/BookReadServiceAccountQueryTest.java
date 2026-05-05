package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.BookReadServiceTestSupport.CASH_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.EUR_DEBIT_BALANCE;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.REVENUE_ACCOUNT;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.declareDefaultAccounts;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.initializedCountingBook;
import static dev.erst.fingrind.executor.BookReadServiceTestSupport.postingFact;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookInspection;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPublishedLanguageTranslator;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Unit tests covering account and posting queries in {@link BookReadService}. */
@NullUnmarked
class BookReadServiceAccountQueryTest {
  @Test
  void constructor_rejectsNullSession() {
    assertThrows(NullPointerException.class, () -> new BookReadService(null));
  }

  @Test
  void inspectBook_delegatesToSessionInspection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      BookInspection inspection = bookSession.inspectBook();
      assertEquals(inspection, service.inspectBook());
    }
  }

  @Test
  void listAccounts_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listAccounts(new ListAccountsQuery(50, Optional.empty())));
    }
  }

  @Test
  void listAccounts_returnsDeclaredAccountsWhenInitialized() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new ListAccountsResult.Listed(
              new AccountPage(List.of(CASH_ACCOUNT, REVENUE_ACCOUNT), 50, Optional.empty())),
          service.listAccounts(new ListAccountsQuery(50, Optional.empty())));
    }
  }

  @Test
  void getPosting_rejectsUninitializedAndMissingPosting() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.getPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new GetPostingResult.Rejected(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-1"))),
          service.getPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void listPostings_rejectsUnknownFilteredAccount() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

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
      BookReadService service = new BookReadService(uninitializedBook);

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
      BookReadService service = new BookReadService(bookSession);

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
    BookReadService service = new BookReadService(bookSession);

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
  void findHelpers_returnEmptyForUninitializedBooksAndDelegateWhenInitialized() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(Optional.empty(), service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(Optional.empty(), service.findPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      var postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          Optional.of(BookReadServiceTestSupport.REGISTERED_CASH_ACCOUNT),
          service.findAccount(CASH_ACCOUNT.accountCode()));
      assertEquals(Optional.of(postingFact), service.findPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void accountBalance_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookReadService service = new BookReadService(uninitializedBook);

      assertEquals(
          new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountBalance(new AccountBalanceQuery(CASH_ACCOUNT.accountCode(), null, null)));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertEquals(
          new AccountBalanceResult.Rejected(
              new BookQueryRejection.UnknownAccount(CASH_ACCOUNT.accountCode())),
          service.accountBalance(new AccountBalanceQuery(CASH_ACCOUNT.accountCode(), null, null)));
    }
  }

  @Test
  void readMethods_rejectNullInputs() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookReadService service = new BookReadService(bookSession);

      assertThrows(NullPointerException.class, () -> service.listAccounts(null));
      assertThrows(NullPointerException.class, () -> service.getPosting(null));
      assertThrows(NullPointerException.class, () -> service.findAccount(null));
      assertThrows(NullPointerException.class, () -> service.findPosting(null));
      assertThrows(NullPointerException.class, () -> service.listPostings(null));
      assertThrows(NullPointerException.class, () -> service.accountBalance(null));
      assertThrows(NullPointerException.class, () -> service.trialBalance(null));
      assertThrows(NullPointerException.class, () -> service.accountLedger(null));
      assertThrows(NullPointerException.class, () -> service.periodSummary(null));
    }
  }
}
