package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import dev.erst.fingrind.runtime.InMemoryPostingFactStore;
import dev.erst.fingrind.runtime.PostingFact;
import dev.erst.fingrind.runtime.PostingFactStore;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostingApplicationService}. */
class PostingApplicationServiceTest {
  @Test
  void preflight_returnsAcceptedWhenIdempotencyKeyIsUnused() {
    PostingApplicationService applicationService =
        new PostingApplicationService(
            new InMemoryPostingFactStore(), () -> new PostingId("posting-1"));

    PostEntryResult result = applicationService.preflight(command("idem-1"));

    assertEquals(
        new PostEntryResult.PreflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
        result);
  }

  @Test
  void preflight_returnsRejectedWhenIdempotencyKeyAlreadyExists() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(postingFact("idem-1"));
    PostingApplicationService applicationService =
        new PostingApplicationService(postingFactStore, () -> new PostingId("posting-1"));

    PostEntryResult result = applicationService.preflight(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
            "A posting with the same idempotency key already exists in this book.",
            new IdempotencyKey("idem-1")),
        result);
  }

  @Test
  void commit_returnsCommittedWhenIdempotencyKeyIsUnused() {
    PostingApplicationService applicationService =
        new PostingApplicationService(
            new InMemoryPostingFactStore(), () -> new PostingId("posting-1"));

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z")),
        result);
  }

  @Test
  void commit_returnsRejectedWhenIdempotencyKeyAlreadyExists() {
    InMemoryPostingFactStore postingFactStore = new InMemoryPostingFactStore();
    postingFactStore.commit(postingFact("idem-1"));
    PostingApplicationService applicationService =
        new PostingApplicationService(postingFactStore, () -> new PostingId("posting-2"));

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
            "A posting with the same idempotency key already exists in this book.",
            new IdempotencyKey("idem-1")),
        result);
  }

  @Test
  void commit_mapsStoreFailureToDeterministicRejection() {
    PostingFactStore postingFactStore =
        new PostingFactStore() {
          @Override
          public Optional<PostingFact> findByIdempotency(IdempotencyKey idempotencyKey) {
            return Optional.empty();
          }

          @Override
          public PostingFact commit(PostingFact postingFact) {
            throw new IllegalStateException("boom");
          }
        };
    PostingApplicationService applicationService =
        new PostingApplicationService(postingFactStore, () -> new PostingId("posting-1"));

    PostEntryResult result = applicationService.commit(command("idem-1"));

    assertEquals(
        new PostEntryResult.Rejected(
            PostingRejectionCode.DUPLICATE_IDEMPOTENCY_KEY,
            "A posting with the same idempotency key already exists in this book.",
            new IdempotencyKey("idem-1")),
        result);
  }

  private static PostEntryCommand command(String idempotencyKey) {
    return new PostEntryCommand(journalEntry(), provenance(idempotencyKey));
  }

  private static PostingFact postingFact(String idempotencyKey) {
    return new PostingFact(
        new PostingId("posting-existing"), journalEntry(), provenance(idempotencyKey));
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
                new Money(new CurrencyCode("EUR"), new BigDecimal("10.00")))),
        Optional.empty());
  }

  private static ProvenanceEnvelope provenance(String idempotencyKey) {
    return new ProvenanceEnvelope(
        "actor-1",
        ProvenanceEnvelope.ActorType.AGENT,
        "command-1",
        new IdempotencyKey(idempotencyKey),
        "cause-1",
        Optional.empty(),
        Instant.parse("2026-04-07T10:15:30Z"),
        Optional.empty(),
        ProvenanceEnvelope.SourceChannel.TEST);
  }
}
