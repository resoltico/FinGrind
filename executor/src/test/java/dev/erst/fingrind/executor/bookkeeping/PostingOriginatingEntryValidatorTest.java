package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for retained originating-entry validator branches. */
class PostingOriginatingEntryValidatorTest {
  @Test
  void requireResolvedMatches_allowsNullAndMatchingEntries() {
    BookkeepingEntry.SaleSettled sale = saleEntry();

    assertDoesNotThrow(
        () ->
            PostingOriginatingEntryValidator.requireResolvedMatches(
                null,
                PostingKind.STANDARD,
                PostingOriginKind.SALE_SETTLED,
                sale.journalEntry(),
                PostingLineageModel.direct(),
                "subject"));
    assertDoesNotThrow(
        () ->
            PostingOriginatingEntryValidator.requireResolvedMatches(
                sale,
                PostingKind.STANDARD,
                PostingOriginKind.SALE_SETTLED,
                sale.journalEntry(),
                PostingLineageModel.direct(),
                "subject"));
  }

  @Test
  void requireResolvedMatches_rejectsKindOriginJournalAndLineageDrift() {
    BookkeepingEntry.SaleSettled sale = saleEntry();
    IllegalArgumentException postingKindFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireResolvedMatches(
                    sale,
                    PostingKind.OPENING_BALANCE,
                    PostingOriginKind.SALE_SETTLED,
                    sale.journalEntry(),
                    PostingLineageModel.direct(),
                    "subject"));
    IllegalArgumentException postingOriginFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireResolvedMatches(
                    sale,
                    PostingKind.STANDARD,
                    PostingOriginKind.OWNER_CONTRIBUTION,
                    sale.journalEntry(),
                    PostingLineageModel.direct(),
                    "subject"));
    IllegalArgumentException journalFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireResolvedMatches(
                    sale,
                    PostingKind.STANDARD,
                    PostingOriginKind.SALE_SETTLED,
                    mismatchedJournalEntry(),
                    PostingLineageModel.direct(),
                    "subject"));
    IllegalArgumentException lineageFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireResolvedMatches(
                    sale,
                    PostingKind.STANDARD,
                    PostingOriginKind.SALE_SETTLED,
                    sale.journalEntry(),
                    PostingLineageModel.reversal(
                        new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                        new ReversalReason("operator reversal")),
                    "subject"));

    assertEquals(
        "resolvedOriginatingEntry postingKind must match the subject.",
        postingKindFailure.getMessage());
    assertEquals(
        "resolvedOriginatingEntry postingOriginKind must match the subject.",
        postingOriginFailure.getMessage());
    assertEquals(
        "resolvedOriginatingEntry journalEntry must match the subject journalEntry.",
        journalFailure.getMessage());
    assertEquals(
        "resolvedOriginatingEntry postingLineage must match the subject lineage.",
        lineageFailure.getMessage());
  }

  @Test
  void requireCallerAuthoredMatches_allowsNullAndRejectsKindOriginAndLineageDrift() {
    BookkeepingEntry.SaleSettled sale = saleEntry();

    assertDoesNotThrow(
        () ->
            PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
                null,
                PostingKind.STANDARD,
                PostingOriginKind.SALE_SETTLED,
                PostingLineageModel.direct(),
                "subject"));

    IllegalArgumentException postingKindFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
                    sale,
                    PostingKind.OPENING_BALANCE,
                    PostingOriginKind.SALE_SETTLED,
                    PostingLineageModel.direct(),
                    "subject"));
    IllegalArgumentException postingOriginFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
                    sale,
                    PostingKind.STANDARD,
                    PostingOriginKind.OWNER_CONTRIBUTION,
                    PostingLineageModel.direct(),
                    "subject"));
    IllegalArgumentException lineageFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                PostingOriginatingEntryValidator.requireCallerAuthoredMatches(
                    sale,
                    PostingKind.STANDARD,
                    PostingOriginKind.SALE_SETTLED,
                    PostingLineageModel.reversal(
                        new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                        new ReversalReason("operator reversal")),
                    "subject"));

    assertEquals(
        "callerAuthoredEntry postingKind must match the subject.", postingKindFailure.getMessage());
    assertEquals(
        "callerAuthoredEntry postingOriginKind must match the subject.",
        postingOriginFailure.getMessage());
    assertEquals(
        "callerAuthoredEntry postingLineage must match the subject lineage.",
        lineageFailure.getMessage());
  }

  @Test
  void requireResolvedMatches_acceptsMatchingReversalLineage() {
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                new ReversalReason("operator reversal")),
            null,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.parse("EUR", "10.00")),
                    new JournalLine(
                        new AccountCode("4000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.parse("EUR", "10.00")))));

    assertDoesNotThrow(
        () ->
            PostingOriginatingEntryValidator.requireResolvedMatches(
                reversal,
                PostingKind.STANDARD,
                PostingOriginKind.REVERSAL,
                reversal.journalEntry(),
                PostingLineageModel.reversal(
                    new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new ReversalReason("operator reversal")),
                "subject"));
  }

  private static BookkeepingEntry.SaleSettled saleEntry() {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1000"),
        null,
        null,
        null,
        null,
        null);
  }

  private static JournalEntry mismatchedJournalEntry() {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode("1000"), JournalLine.EntrySide.DEBIT, Money.parse("EUR", "10.00")),
            new JournalLine(
                new AccountCode("4999"),
                JournalLine.EntrySide.CREDIT,
                Money.parse("EUR", "10.00"))));
  }
}
