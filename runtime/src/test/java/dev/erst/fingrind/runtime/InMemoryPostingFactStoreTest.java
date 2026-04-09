package dev.erst.fingrind.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link InMemoryPostingFactStore}. */
class InMemoryPostingFactStoreTest {
  @Test
  void findByIdempotency_returnsEmptyWhenKeyIsUnknown() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();

    assertEquals(
        Optional.empty(), postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
  }

  @Test
  void commit_storesPostingFact() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    PostingFact postingFact = postingFact("idem-1");

    postingFactStore.commit(postingFact);

    assertEquals(
        Optional.of(postingFact), postingFactStore.findByIdempotency(new IdempotencyKey("idem-1")));
  }

  @Test
  void commit_rejectsDuplicateIdempotencyKey() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(postingFact("idem-1"));

    assertThrows(IllegalStateException.class, () -> postingFactStore.commit(postingFact("idem-1")));
  }

  private static PostingFact postingFact(String idempotencyKey) {
    return new PostingFact(
        new PostingId("posting-" + idempotencyKey),
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00"))),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")))),
            Optional.empty()),
        new ProvenanceEnvelope(
            "actor-1",
            ProvenanceEnvelope.ActorType.AGENT,
            "command-" + idempotencyKey,
            new IdempotencyKey(idempotencyKey),
            "cause-1",
            Optional.empty(),
            Instant.parse("2026-04-07T10:15:30Z"),
            Optional.empty(),
            ProvenanceEnvelope.SourceChannel.TEST));
  }
}
