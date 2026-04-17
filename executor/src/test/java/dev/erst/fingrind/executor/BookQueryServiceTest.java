package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.AccountBalanceQuery;
import dev.erst.fingrind.contract.AccountBalanceResult;
import dev.erst.fingrind.contract.AccountBalanceSnapshot;
import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookQueryRejection;
import dev.erst.fingrind.contract.CurrencyBalance;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.GetPostingResult;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.ListAccountsResult;
import dev.erst.fingrind.contract.ListPostingsQuery;
import dev.erst.fingrind.contract.ListPostingsResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingPage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BookQueryService}. */
class BookQueryServiceTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void inspectBook_delegatesToSessionInspection() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(bookSession.inspectBook(), service.inspectBook());
    }
  }

  @Test
  void listAccounts_rejectsUninitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new ListAccountsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listAccounts(new ListAccountsQuery(50, 0)));
    }
  }

  @Test
  void listAccounts_returnsDeclaredAccountsWhenInitialized() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new ListAccountsResult.Listed(
              new AccountPage(
                  List.of(
                      new DeclaredAccount(
                          new AccountCode("1000"),
                          new AccountName("Cash"),
                          NormalBalance.DEBIT,
                          true,
                          FIXED_INSTANT),
                      new DeclaredAccount(
                          new AccountCode("2000"),
                          new AccountName("Revenue"),
                          NormalBalance.CREDIT,
                          true,
                          FIXED_INSTANT)),
                  50,
                  0,
                  false)),
          service.listAccounts(new ListAccountsQuery(50, 0)));
    }
  }

  @Test
  void getPosting_rejectsUninitializedAndMissingPosting() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookQueryService service = new BookQueryService(uninitializedBook);

      assertEquals(
          new GetPostingResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.getPosting(new PostingId("posting-1")));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new GetPostingResult.Rejected(
              new BookQueryRejection.PostingNotFound(new PostingId("posting-1"))),
          service.getPosting(new PostingId("posting-1")));
    }
  }

  @Test
  void listPostings_rejectsUnknownFilteredAccount() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new ListPostingsResult.Rejected(
              new BookQueryRejection.UnknownAccount(new AccountCode("9999"))),
          service.listPostings(
              new ListPostingsQuery(
                  Optional.of(new AccountCode("9999")),
                  Optional.empty(),
                  Optional.empty(),
                  20,
                  0)));
    }
  }

  @Test
  void listPostings_rejectsUninitializedBookAndListsCommittedPostings() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookQueryService service = new BookQueryService(uninitializedBook);

      assertEquals(
          new ListPostingsResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), Optional.empty(), Optional.empty(), 20, 0)));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new ListPostingsResult.Listed(new PostingPage(List.of(postingFact), 20, 0, false)),
          service.listPostings(
              new ListPostingsQuery(Optional.empty(), Optional.empty(), Optional.empty(), 20, 0)));
    }
  }

  @Test
  void getPostingAndAccountBalance_returnCommittedSnapshots() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      declareDefaultAccounts(bookSession);
      PostingFact postingFact = postingFact("posting-1", "idem-1");
      bookSession.commit(postingFact);
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new GetPostingResult.Found(postingFact), service.getPosting(new PostingId("posting-1")));
      assertEquals(
          new AccountBalanceResult.Reported(
              new AccountBalanceSnapshot(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      FIXED_INSTANT),
                  Optional.empty(),
                  Optional.empty(),
                  List.of(
                      new CurrencyBalance(
                          new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                          new Money(new CurrencyCode("EUR"), BigDecimal.ZERO),
                          new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")),
                          NormalBalance.DEBIT)))),
          service.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"), Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void accountBalance_rejectsUninitializedAndUnknownAccount() {
    try (InMemoryBookSession uninitializedBook = new InMemoryBookSession()) {
      BookQueryService service = new BookQueryService(uninitializedBook);

      assertEquals(
          new AccountBalanceResult.Rejected(new BookQueryRejection.BookNotInitialized()),
          service.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"), Optional.empty(), Optional.empty())));
    }
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertEquals(
          new AccountBalanceResult.Rejected(
              new BookQueryRejection.UnknownAccount(new AccountCode("1000"))),
          service.accountBalance(
              new AccountBalanceQuery(
                  new AccountCode("1000"), Optional.empty(), Optional.empty())));
    }
  }

  @Test
  void queryMethods_rejectNullInputs() {
    try (InMemoryBookSession bookSession = initializedBook()) {
      BookQueryService service = new BookQueryService(bookSession);

      assertThrows(NullPointerException.class, () -> service.listAccounts(null));
      assertThrows(NullPointerException.class, () -> service.getPosting(null));
      assertThrows(NullPointerException.class, () -> service.listPostings(null));
      assertThrows(NullPointerException.class, () -> service.accountBalance(null));
    }
  }

  private static InMemoryBookSession initializedBook() {
    InMemoryBookSession bookSession = new InMemoryBookSession();
    bookSession.openBook(FIXED_INSTANT);
    return bookSession;
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT, FIXED_INSTANT);
    bookSession.declareAccount(
        new AccountCode("2000"), new AccountName("Revenue"), NormalBalance.CREDIT, FIXED_INSTANT);
  }

  private static PostingFact postingFact(String postingId, String idempotencyKey) {
    return new PostingFact(
        new PostingId(postingId),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
                line("2000", JournalLine.EntrySide.CREDIT, "10.00"))),
        Optional.empty(),
        new CommittedProvenance(
            new RequestProvenance(
                new ActorId("actor-1"),
                ActorType.AGENT,
                new CommandId("command-1"),
                new IdempotencyKey(idempotencyKey),
                new CausationId("cause-1"),
                Optional.empty(),
                Optional.empty()),
            FIXED_INSTANT,
            SourceChannel.CLI));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }
}
