package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.PostingFact;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
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
            new PostingId("posting-1"),
            journalEntry(),
            PostingLineage.direct(),
            PostingKind.STANDARD,
            ContractFixtures.accountingEvidence("idem-1"),
            provenance("idem-1"));
    assertEquals("posting-1", postingFact.postingId().value());
  }

  @Test
  void constructor_rejectsNullPostingId() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostingFact(
                nullOf(),
                journalEntry(),
                PostingLineage.direct(),
                PostingKind.STANDARD,
                ContractFixtures.accountingEvidence("idem-1"),
                provenance("idem-1")));
  }

  @Test
  void constructor_rejectsNullPostingLineage() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostingFact(
                new PostingId("posting-1"),
                journalEntry(),
                nullOf(),
                PostingKind.STANDARD,
                ContractFixtures.accountingEvidence("idem-1"),
                provenance("idem-1")));
  }

  private static JournalEntry journalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("2000"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }

  private static CommittedProvenance provenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.of(new CorrelationId("corr-1"))),
        Instant.parse("2026-04-07T10:15:30Z"),
        SourceChannel.CLI);
  }
}
