package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import dev.erst.fingrind.core.SourceChannel;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link PostEntryCommand}. */
class PostEntryCommandTest {
  @Test
  void constructor_acceptsValidCommand() {
    BookkeepingEntry.ManualAdjustment adjustment =
        new BookkeepingEntry.ManualAdjustment(
            PostingKind.STANDARD,
            journalEntry(),
            PostingLineage.reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("operator reversal")));
    PostEntryCommand command =
        new PostEntryCommand(
            adjustment,
            ContractFixtures.accountingEvidence("idem-1"),
            ContractFixtures.requestProvenance("idem-1"),
            SourceChannel.CLI);
    assertEquals(LocalDate.parse("2026-04-07"), command.entry().effectiveDate());
    assertEquals(BookkeepingEntry.ManualAdjustment.class, command.entry().getClass());
    assertEquals(PostingKind.STANDARD, adjustment.postingKind());
    assertEquals(1, adjustment.postingLineage().reversalReference().stream().count());
    assertEquals(SourceChannel.CLI, command.sourceChannel());
  }

  @Test
  void constructor_rejectsNullEntry() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostEntryCommand(
                nullOf(),
                ContractFixtures.accountingEvidence("idem-1"),
                ContractFixtures.requestProvenance("idem-1"),
                SourceChannel.CLI));
  }

  @Test
  void constructor_rejectsNullEvidence() {
    assertThrows(
        NullPointerException.class,
        () ->
            new PostEntryCommand(
                new BookkeepingEntry.ManualAdjustment(
                    PostingKind.STANDARD, journalEntry(), PostingLineage.direct()),
                nullOf(),
                ContractFixtures.requestProvenance("idem-1"),
                SourceChannel.CLI));
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
}
