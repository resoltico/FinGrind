package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.AccountPage;
import dev.erst.fingrind.contract.BookAdministrationRejection;
import dev.erst.fingrind.contract.DeclareAccountResult;
import dev.erst.fingrind.contract.DeclaredAccount;
import dev.erst.fingrind.contract.ListAccountsQuery;
import dev.erst.fingrind.contract.OpenBookResult;
import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.contract.PostingRejection;
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
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InMemoryBookSession}. */
class InMemoryBookSessionTest {
  private static final Instant FIXED_INSTANT = Instant.parse("2026-04-07T10:15:30Z");

  @Test
  void openBook_marksSessionInitializedAndRejectsSecondOpen() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertFalse(bookSession.isInitialized());
      assertEquals(new OpenBookResult.Opened(FIXED_INSTANT), bookSession.openBook(FIXED_INSTANT));
      assertTrue(bookSession.isInitialized());
      assertEquals(
          new OpenBookResult.Rejected(new BookAdministrationRejection.BookAlreadyInitialized()),
          bookSession.openBook(FIXED_INSTANT));
    }
  }

  @Test
  void declareAccount_requiresInitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new DeclareAccountResult.Rejected(new BookAdministrationRejection.BookNotInitialized()),
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.DEBIT,
              FIXED_INSTANT));
    }
  }

  @Test
  void declareAccount_storesAndListsAccountSnapshots() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);

      DeclareAccountResult result =
          bookSession.declareAccount(
              new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT, FIXED_INSTANT);

      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash"),
                  NormalBalance.DEBIT,
                  true,
                  FIXED_INSTANT)),
          result);
      assertEquals(
          new AccountPage(
              List.of(
                  new DeclaredAccount(
                      new AccountCode("1000"),
                      new AccountName("Cash"),
                      NormalBalance.DEBIT,
                      true,
                      FIXED_INSTANT)),
              50,
              0,
              false),
          bookSession.listAccounts(new ListAccountsQuery(50, 0)));
    }
  }

  @Test
  void declareAccount_reactivatesExistingAccountWithoutChangingDeclaredAt() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);
      bookSession.declareAccount(
          new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT, FIXED_INSTANT);
      bookSession.deactivateAccount(new AccountCode("1000"));

      DeclareAccountResult result =
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash main"),
              NormalBalance.DEBIT,
              Instant.parse("2026-04-08T11:00:00Z"));

      assertEquals(
          new DeclareAccountResult.Declared(
              new DeclaredAccount(
                  new AccountCode("1000"),
                  new AccountName("Cash main"),
                  NormalBalance.DEBIT,
                  true,
                  FIXED_INSTANT)),
          result);
    }
  }

  @Test
  void declareAccount_rejectsNormalBalanceConflict() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);
      bookSession.declareAccount(
          new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT, FIXED_INSTANT);

      DeclareAccountResult result =
          bookSession.declareAccount(
              new AccountCode("1000"),
              new AccountName("Cash"),
              NormalBalance.CREDIT,
              FIXED_INSTANT);

      assertEquals(
          new DeclareAccountResult.Rejected(
              new BookAdministrationRejection.NormalBalanceConflict(
                  new AccountCode("1000"), NormalBalance.DEBIT, NormalBalance.CREDIT)),
          result);
    }
  }

  @Test
  void commit_requiresInitializedBook() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(
          new PostingCommitResult.Rejected(new PostingRejection.BookNotInitialized()),
          bookSession.commit(postingFact("idem-1")));
    }
  }

  @Test
  void commit_rejectsUnknownAndInactiveAccounts() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);
      assertEquals(
          new PostingCommitResult.Rejected(
              new PostingRejection.AccountStateViolations(
                  List.of(
                      new PostingRejection.UnknownAccount(new AccountCode("1000")),
                      new PostingRejection.UnknownAccount(new AccountCode("2000"))))),
          bookSession.commit(postingFact("idem-1")));

      declareDefaultAccounts(bookSession);
      bookSession.deactivateAccount(new AccountCode("1000"));

      assertEquals(
          new PostingCommitResult.Rejected(
              new PostingRejection.AccountStateViolations(
                  List.of(new PostingRejection.InactiveAccount(new AccountCode("1000"))))),
          bookSession.commit(postingFact("idem-2")));
    }
  }

  @Test
  void commit_storesPostingAndDuplicateOutcomesAfterInitialization() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);
      declareDefaultAccounts(bookSession);
      PostingFact originalPosting = postingFact("idem-original");
      PostingFact firstReversal = reversalFact("idem-reversal-1", "posting-idem-original");
      PostingFact secondReversal = reversalFact("idem-reversal-2", "posting-idem-original");

      assertEquals(
          new PostingCommitResult.Committed(originalPosting), bookSession.commit(originalPosting));
      assertEquals(
          Optional.of(originalPosting),
          bookSession.findExistingPosting(new IdempotencyKey("idem-original")));
      assertEquals(
          Optional.of(originalPosting),
          bookSession.findPosting(new PostingId("posting-idem-original")));
      assertEquals(
          new PostingCommitResult.Rejected(new PostingRejection.DuplicateIdempotencyKey()),
          bookSession.commit(postingFact("idem-original")));

      assertEquals(
          new PostingCommitResult.Committed(firstReversal), bookSession.commit(firstReversal));
      assertEquals(
          Optional.of(firstReversal),
          bookSession.findReversalFor(new PostingId("posting-idem-original")));
      assertEquals(
          new PostingCommitResult.Rejected(
              new PostingRejection.ReversalAlreadyExists(new PostingId("posting-idem-original"))),
          bookSession.commit(secondReversal));
    }
  }

  @Test
  void close_isANoOp() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertDoesNotThrow(bookSession::close);
    }
  }

  @Test
  void deactivateAccount_rejectsUnknownAccount() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.openBook(FIXED_INSTANT);

      IllegalArgumentException thrown =
          org.junit.jupiter.api.Assertions.assertThrows(
              IllegalArgumentException.class,
              () -> bookSession.deactivateAccount(new AccountCode("9999")));

      assertTrue(thrown.getMessage().contains("9999"));
    }
  }

  private static void declareDefaultAccounts(InMemoryBookSession bookSession) {
    bookSession.declareAccount(
        new AccountCode("1000"), new AccountName("Cash"), NormalBalance.DEBIT, FIXED_INSTANT);
    bookSession.declareAccount(
        new AccountCode("2000"), new AccountName("Revenue"), NormalBalance.CREDIT, FIXED_INSTANT);
  }

  private static PostingFact postingFact(String idempotencyKey) {
    return new PostingFact(
        new PostingId("posting-" + idempotencyKey),
        journalEntry(),
        PostingLineage.direct(),
        committedProvenance(idempotencyKey));
  }

  private static PostingFact reversalFact(String idempotencyKey, String priorPostingId) {
    return new PostingFact(
        new PostingId("posting-" + idempotencyKey),
        reversalJournalEntry(),
        PostingLineage.reversal(
            new ReversalReference(new PostingId(priorPostingId)),
            new ReversalReason("historical full reversal")),
        committedProvenance(idempotencyKey));
  }

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-" + idempotencyKey),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.empty()),
        FIXED_INSTANT,
        SourceChannel.CLI);
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.DEBIT, "10.00"),
            line("2000", JournalLine.EntrySide.CREDIT, "10.00")));
  }

  private static JournalEntry reversalJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            line("1000", JournalLine.EntrySide.CREDIT, "10.00"),
            line("2000", JournalLine.EntrySide.DEBIT, "10.00")));
  }

  private static JournalLine line(String accountCode, JournalLine.EntrySide side, String amount) {
    return new JournalLine(
        new AccountCode(accountCode),
        side,
        new Money(new CurrencyCode("EUR"), new BigDecimal(amount)));
  }
}
