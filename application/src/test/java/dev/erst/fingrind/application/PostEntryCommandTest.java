package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.ProvenanceEnvelope;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostEntryCommand}. */
class PostEntryCommandTest {
  @Test
  void constructor_acceptsValidCommand() {
    PostEntryCommand command = new PostEntryCommand(journalEntry(), provenance("idem-1"));

    assertEquals(LocalDate.parse("2026-04-07"), command.journalEntry().effectiveDate());
  }

  @Test
  void constructor_rejectsNullJournalEntry() {
    assertThrows(
        NullPointerException.class, () -> new PostEntryCommand(null, provenance("idem-1")));
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
