package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
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
  @Test
  void findByIdempotency_returnsEmptyWhenKeyIsUnknown() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertEquals(Optional.empty(), bookSession.findByIdempotency(new IdempotencyKey("idem-1")));
    }
  }

  @Test
  void commit_storesPostingFact() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingFact postingFact = postingFact("idem-1");

      PostingCommitResult result = bookSession.commit(postingFact);

      assertEquals(new PostingCommitResult.Committed(postingFact), result);
      assertEquals(
          Optional.of(postingFact), bookSession.findByIdempotency(new IdempotencyKey("idem-1")));
    }
  }

  @Test
  void commit_returnsDuplicateIdempotencyOutcome() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.commit(postingFact("idem-1"));

      PostingCommitResult result = bookSession.commit(postingFact("idem-1"));

      assertEquals(
          new PostingCommitResult.DuplicateIdempotency(new IdempotencyKey("idem-1")), result);
    }
  }

  @Test
  void findByPostingId_returnsCommittedPostingWhenPresent() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingFact postingFact = postingFact("idem-1");
      bookSession.commit(postingFact);

      assertEquals(
          Optional.of(postingFact), bookSession.findByPostingId(new PostingId("posting-idem-1")));
    }
  }

  @Test
  void commit_rejectsSecondReversalForSameTargetWithoutStoringIt() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.commit(postingFact("idem-original"));
      bookSession.commit(reversalFact("idem-reversal-1", "posting-idem-original"));

      PostingFact duplicateReversal = reversalFact("idem-reversal-2", "posting-idem-original");
      PostingCommitResult result = bookSession.commit(duplicateReversal);

      assertEquals(
          new PostingCommitResult.DuplicateReversalTarget(new PostingId("posting-idem-original")),
          result);
      assertEquals(
          Optional.empty(), bookSession.findByIdempotency(new IdempotencyKey("idem-reversal-2")));
    }
  }

  @Test
  void findReversalFor_returnsCommittedReversalWhenPresent() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      bookSession.commit(postingFact("idem-original"));
      PostingFact reversalFact = reversalFact("idem-reversal", "posting-idem-original");
      bookSession.commit(reversalFact);

      assertEquals(
          Optional.of(reversalFact),
          bookSession.findReversalFor(new PostingId("posting-idem-original")));
    }
  }

  @Test
  void commit_doesNotRegisterOrdinaryPostingAsReversal() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      PostingFact postingFact = postingFact("idem-original");

      PostingCommitResult result = bookSession.commit(postingFact);

      assertEquals(new PostingCommitResult.Committed(postingFact), result);
      assertEquals(
          Optional.empty(), bookSession.findReversalFor(new PostingId("posting-idem-original")));
    }
  }

  @Test
  void close_isANoOp() {
    try (InMemoryBookSession bookSession = new InMemoryBookSession()) {
      assertDoesNotThrow(bookSession::close);
    }
  }

  private static PostingFact postingFact(String idempotencyKey) {
    return new PostingFact(
        new PostingId("posting-" + idempotencyKey),
        journalEntry(),
        Optional.empty(),
        committedProvenance(idempotencyKey, Optional.empty()));
  }

  private static PostingFact reversalFact(String idempotencyKey, String priorPostingId) {
    return new PostingFact(
        new PostingId("posting-" + idempotencyKey),
        reversalJournalEntry(),
        Optional.of(new ReversalReference(new PostingId(priorPostingId))),
        committedProvenance(
            idempotencyKey, Optional.of(new ReversalReason("historical full reversal"))));
  }

  private static CommittedProvenance committedProvenance(
      String idempotencyKey, Optional<ReversalReason> reason) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-" + idempotencyKey),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1")),
            reason),
        Instant.parse("2026-04-07T10:15:30Z"),
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
