package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.PostingFact;
import dev.erst.fingrind.contract.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
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
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for the executor-owned posting draft. */
class PostingDraftTest {
  @Test
  void postingDraft_keepsExplicitMissingReversalAndMaterializesPostingFacts() {
    PostingDraft postingDraft =
        new PostingDraft(journalEntry(), PostingLineage.direct(), committedProvenance("idem-1"));
    PostingFact postingFact = postingDraft.materialize(new PostingId("posting-1"));

    assertTrue(postingDraft.reversalReference().isEmpty());
    assertEquals(postingDraft.provenance().requestProvenance(), postingDraft.requestProvenance());
    assertEquals(new PostingId("posting-1"), postingFact.postingId());
    assertEquals(postingDraft.journalEntry(), postingFact.journalEntry());
    assertTrue(postingFact.reversalReference().isEmpty());
    assertEquals(postingDraft.provenance(), postingFact.provenance());
  }

  @Test
  void postingDraft_rejectsNullPostingLineage() {
    assertThrows(
        NullPointerException.class,
        () -> new PostingDraft(journalEntry(), null, committedProvenance("idem-1")));
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

  private static CommittedProvenance committedProvenance(String idempotencyKey) {
    return new CommittedProvenance(
        new RequestProvenance(
            new ActorId("actor-1"),
            ActorType.AGENT,
            new CommandId("command-1"),
            new IdempotencyKey(idempotencyKey),
            new CausationId("cause-1"),
            Optional.empty()),
        Instant.parse("2026-04-07T10:15:30Z"),
        SourceChannel.CLI);
  }
}
