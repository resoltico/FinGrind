package dev.erst.fingrind.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/** Unit tests for {@link PostingFact}. */
class PostingFactTest {
  @Test
  void constructor_acceptsValidFact() {
    PostingFact postingFact =
        new PostingFact(
            new PostingId("posting-1"), journalEntry(), Optional.empty(), provenance("idem-1"));

    assertEquals("posting-1", postingFact.postingId().value());
  }

  @Test
  void constructor_rejectsNullPostingId() {
    assertThrows(
        NullPointerException.class,
        () -> new PostingFact(null, journalEntry(), Optional.empty(), provenance("idem-1")));
  }

  @Test
  void constructor_normalizesNullReversalReferenceToEmpty() {
    PostingFact postingFact =
        new PostingFact(
            new PostingId("posting-1"),
            journalEntry(),
            nullReversalReference(),
            provenance("idem-1"));

    assertEquals(Optional.empty(), postingFact.reversalReference());
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")))));
  }

  private static CommittedProvenance provenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1")),
            Optional.of(new ReversalReason("operator reversal"))),
        Instant.parse("2026-04-07T10:15:30Z"),
        SourceChannel.CLI);
  }

  private static Optional<ReversalReference> nullReversalReference() {
    return null;
  }
}
