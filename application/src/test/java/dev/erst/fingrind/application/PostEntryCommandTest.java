package dev.erst.fingrind.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.ActorId;
import dev.erst.fingrind.core.ActorType;
import dev.erst.fingrind.core.CausationId;
import dev.erst.fingrind.core.CommandId;
import dev.erst.fingrind.core.CorrectionReason;
import dev.erst.fingrind.core.CorrectionReference;
import dev.erst.fingrind.core.CorrelationId;
import dev.erst.fingrind.core.CurrencyCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.RequestProvenance;
import dev.erst.fingrind.core.SourceChannel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostEntryCommand}. */
class PostEntryCommandTest {
  @Test
  void constructor_acceptsValidCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            journalEntry(),
            Optional.of(
                new CorrectionReference(
                    CorrectionReference.CorrectionKind.AMENDMENT, new PostingId("posting-1"))),
            requestProvenance("idem-1", Optional.of(new CorrectionReason("operator correction"))),
            SourceChannel.CLI);

    assertEquals(LocalDate.parse("2026-04-07"), command.journalEntry().effectiveDate());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void constructor_rejectsNullJournalEntry() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostEntryCommand(
                null, Optional.empty(), requestProvenance("idem-1"), SourceChannel.CLI));
  }

  @Test
  void constructor_defaultsNullCorrectionReferenceToEmpty() {
    PostEntryCommand command =
        new PostEntryCommand(
            journalEntry(),
            nullCorrectionReference(),
            requestProvenance("idem-1"),
            SourceChannel.CLI);

    assertEquals(Optional.empty(), command.correctionReference());
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

  private static RequestProvenance requestProvenance(String idempotencyKey) {
    return requestProvenance(idempotencyKey, Optional.empty());
  }

  private static RequestProvenance requestProvenance(
      String idempotencyKey, Optional<CorrectionReason> reason) {
    return new RequestProvenance(
        new ActorId("actor-1"),
        ActorType.AGENT,
        new CommandId("command-1"),
        new IdempotencyKey(idempotencyKey),
        new CausationId("cause-1"),
        Optional.of(new CorrelationId("corr-1")),
        reason);
  }

  @SuppressWarnings("NullOptional")
  private static Optional<CorrectionReference> nullCorrectionReference() {
    return null;
  }
}
