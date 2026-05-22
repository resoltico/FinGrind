package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for the typed public bookkeeping entry surface. */
class BookkeepingEntryTest {
  @Test
  void typedEntries_publishStableKindsAndRequirePositiveAmounts() {
    BookkeepingEntry.CashRevenue cashRevenue =
        new BookkeepingEntry.CashRevenue(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.CashExpense cashExpense =
        new BookkeepingEntry.CashExpense(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.OwnerContribution ownerContribution =
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("3000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.OwnerDraw ownerDraw =
        new BookkeepingEntry.OwnerDraw(
            LocalDate.parse("2026-04-25"),
            new AccountCode("3010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"));

    assertEquals(BookkeepingEntryKind.CASH_REVENUE, cashRevenue.entryKind());
    assertEquals(BookkeepingEntryKind.CASH_EXPENSE, cashExpense.entryKind());
    assertEquals(BookkeepingEntryKind.OWNER_CONTRIBUTION, ownerContribution.entryKind());
    assertEquals(BookkeepingEntryKind.OWNER_DRAW, ownerDraw.entryKind());
    assertEquals(LocalDate.parse("2026-04-25"), ownerDraw.effectiveDate());

    IllegalArgumentException nonPositiveAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookkeepingEntry.CashRevenue(
                    LocalDate.parse("2026-04-25"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "0")));
    assertEquals("amount must carry one positive amount.", nonPositiveAmount.getMessage());
  }

  @Test
  void administrativeAdjustments_exposeEffectiveDateLinesAndEntryKinds() {
    JournalEntry journalEntry = journalEntry();
    BookkeepingEntry.OpeningBalanceAdjustment openingBalanceAdjustment =
        new BookkeepingEntry.OpeningBalanceAdjustment(journalEntry);
    BookkeepingEntry.CorrectionAdjustment correctionAdjustment =
        new BookkeepingEntry.CorrectionAdjustment(journalEntry);
    BookkeepingEntry.ReversalAdjustment reversalAdjustment =
        new BookkeepingEntry.ReversalAdjustment(
            journalEntry,
            new PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")));

    assertEquals(
        BookkeepingEntryKind.OPENING_BALANCE_ADJUSTMENT, openingBalanceAdjustment.entryKind());
    assertEquals(BookkeepingEntryKind.CORRECTION_ADJUSTMENT, correctionAdjustment.entryKind());
    assertEquals(BookkeepingEntryKind.REVERSAL_ADJUSTMENT, reversalAdjustment.entryKind());
    assertEquals(LocalDate.parse("2026-04-07"), openingBalanceAdjustment.effectiveDate());
    assertEquals(LocalDate.parse("2026-04-07"), correctionAdjustment.effectiveDate());
    assertEquals(LocalDate.parse("2026-04-07"), reversalAdjustment.effectiveDate());
    assertEquals(journalEntry.lines(), openingBalanceAdjustment.lines());
    assertEquals(journalEntry.lines(), correctionAdjustment.lines());
    assertEquals(journalEntry.lines(), reversalAdjustment.lines());
  }

  @Test
  void constructors_rejectNullRequiredFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.CashExpense(
                nullOf(),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000")));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-25"),
                nullOf(),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000")));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.OwnerDraw(
                LocalDate.parse("2026-04-25"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                nullOf()));
    assertThrows(
        NullPointerException.class, () -> new BookkeepingEntry.OpeningBalanceAdjustment(nullOf()));
    assertThrows(
        NullPointerException.class, () -> new BookkeepingEntry.CorrectionAdjustment(nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.ReversalAdjustment(journalEntry(), nullOf()));
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
