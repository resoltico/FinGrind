package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CommittedProvenance;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.RequestFingerprint;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.CommittedPosting;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.spi.PostingDraft;
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
        new PostingDraft(
            journalEntry(),
            PostingLineageModel.direct(),
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            accountingEvidence("idem-1"),
            new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
            committedProvenance("idem-1"));
    CommittedPosting postingFact = postingDraft.materialize(new PostingId("posting-1"));
    assertTrue(postingDraft.reversalReference().isEmpty());
    assertEquals(postingDraft.provenance().requestProvenance(), postingDraft.requestProvenance());
    assertEquals(new PostingId("posting-1"), postingFact.postingId());
    assertEquals(postingDraft.journalEntry(), postingFact.journalEntry());
    assertTrue(postingFact.reversalReference().isEmpty());
    assertEquals(postingDraft.provenance(), postingFact.provenance());
  }

  @Test
  @org.jspecify.annotations.NullUnmarked
  void postingDraft_rejectsNullPostingLineage() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostingDraft(
                journalEntry(),
                null,
                PostingKind.STANDARD,
                dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
                accountingEvidence("idem-1"),
                new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                committedProvenance("idem-1")));
  }

  @Test
  void postingDraft_rejectsCallerAuthoredEntryWhenJournalEntryDrifts() {
    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new PostingDraft(
                    journalEntry(),
                    PostingLineageModel.direct(),
                    PostingKind.STANDARD,
                    dev.erst.fingrind.core.PostingOriginKind.SALE,
                    accountingEvidence("idem-2"),
                    new RequestFingerprint(RequestFingerprint.CURRENT_VERSION, "0".repeat(64)),
                    committedProvenance("idem-2"),
                    new BookkeepingEntry.Sale(
                        LocalDate.parse("2026-04-08"),
                        new AccountCode("1000"),
                        new AccountCode("2000"),
                        dev.erst.fingrind.contract.bookkeeping.MonetaryAmount.of(
                            Money.parse("EUR", "10.00")),
                        null,
                        null,
                        null)));

    assertEquals(
        "originatingEntry journalEntry must match the posting draft journalEntry.",
        exception.getMessage());
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
