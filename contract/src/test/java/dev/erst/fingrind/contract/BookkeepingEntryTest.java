package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
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

/** Unit tests for the typed public bookkeeping entry surface. */
class BookkeepingEntryTest {
  @Test
  void typedEntries_publishStableKindsAndRequirePositiveAmounts() {
    BookkeepingEntry.SaleSettled sale =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    BookkeepingEntry.ExpenseSettled expense =
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null);
    BookkeepingEntry.OwnerContribution ownerContribution =
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("3000"),
            new MonetaryAmount("EUR", "1000"),
            null);
    BookkeepingEntry.OwnerWithdrawal ownerWithdrawal =
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-25"),
            new AccountCode("3010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null);

    assertEquals(BookkeepingEntryKind.SALE_SETTLED, sale.entryKind());
    assertEquals(new AccountCode("1000"), sale.cashAccountCode());
    assertEquals(new AccountCode("4000"), sale.revenueAccountCode());
    assertEquals(sale.journalEntry().lines(), sale.lines());
    assertEquals(BookkeepingEntryKind.EXPENSE_SETTLED, expense.entryKind());
    assertEquals(new AccountCode("5000"), expense.expenseAccountCode());
    assertEquals(new AccountCode("1000"), expense.cashAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_CONTRIBUTION, ownerContribution.entryKind());
    assertEquals(new AccountCode("1000"), ownerContribution.cashAccountCode());
    assertEquals(new AccountCode("3000"), ownerContribution.equityAccountCode());
    assertEquals(BookkeepingEntryKind.OWNER_WITHDRAWAL, ownerWithdrawal.entryKind());
    assertEquals(new AccountCode("3010"), ownerWithdrawal.equityAccountCode());
    assertEquals(new AccountCode("1000"), ownerWithdrawal.cashAccountCode());
    assertEquals(LocalDate.parse("2026-04-25"), ownerWithdrawal.effectiveDate());

    IllegalArgumentException nonPositiveAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookkeepingEntry.SaleSettled(
                    LocalDate.parse("2026-04-25"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "0"),
                    null,
                    null,
                    null,
                    null,
                    null));
    assertEquals("amount must carry one positive amount.", nonPositiveAmount.getMessage());
  }

  @Test
  void administrativeEntries_exposeEffectiveDateLinesAndEntryKinds() {
    BookkeepingEntry.DirectJournal directJournal =
        new BookkeepingEntry.DirectJournal(journalEntry(), null);
    BookkeepingEntry.OpeningPosition openingPosition =
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("3000"),
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"),
                    null)));
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            LocalDate.parse("2026-04-07"),
            new PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")),
            null,
            null);

    assertEquals(BookkeepingEntryKind.DIRECT_JOURNAL, directJournal.entryKind());
    assertEquals(journalEntry().lines(), directJournal.lines());
    assertEquals(BookkeepingEntryKind.OPENING_POSITION, openingPosition.entryKind());
    assertEquals(BookkeepingEntryKind.REVERSAL, reversal.entryKind());
    assertEquals(LocalDate.parse("2026-04-07"), openingPosition.effectiveDate());
    assertEquals(LocalDate.parse("2026-04-07"), reversal.effectiveDate());
    assertEquals(2, openingPosition.lines().size());
    assertThrows(IllegalStateException.class, reversal::journalEntry);
    assertThrows(IllegalStateException.class, reversal::lines);
    assertNull(openingPosition.foreignExchangeDetails());
    assertNull(reversal.foreignExchangeDetails());
  }

  @Test
  void entries_publishCanonicalPostingMetadataAndJournalShapes() {
    BookkeepingEntry.DirectJournal directJournal =
        new BookkeepingEntry.DirectJournal(journalEntry(), null);
    BookkeepingEntry.SaleSettled sale =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    BookkeepingEntry.ExpenseSettled expense =
        new BookkeepingEntry.ExpenseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null);
    BookkeepingEntry.OwnerContribution ownerContribution =
        new BookkeepingEntry.OwnerContribution(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("3000"),
            new MonetaryAmount("EUR", "1000"),
            null);
    BookkeepingEntry.OwnerWithdrawal ownerWithdrawal =
        new BookkeepingEntry.OwnerWithdrawal(
            LocalDate.parse("2026-04-25"),
            new AccountCode("3010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            null);
    BookkeepingEntry.OpeningPosition openingPosition =
        new BookkeepingEntry.OpeningPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000"),
                    null),
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("3000"),
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"),
                    null)));
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            journalEntry().effectiveDate(),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("operator reversal")),
            null,
            journalEntry());

    assertEquals(LocalDate.parse("2026-04-07"), directJournal.effectiveDate());
    assertEquals(PostingKind.STANDARD, directJournal.postingKind());
    assertEquals(PostingOriginKind.DIRECT_JOURNAL, directJournal.postingOriginKind());
    assertEquals(PostingLineage.direct(), directJournal.postingLineage());

    assertEquals(PostingKind.STANDARD, sale.postingKind());
    assertEquals(PostingOriginKind.SALE_SETTLED, sale.postingOriginKind());
    assertEquals(sale.journalEntry().lines(), sale.lines());

    assertEquals(PostingKind.STANDARD, expense.postingKind());
    assertEquals(PostingOriginKind.EXPENSE_SETTLED, expense.postingOriginKind());
    assertEquals(expense.journalEntry().lines(), expense.lines());

    assertEquals(PostingKind.STANDARD, ownerContribution.postingKind());
    assertEquals(PostingOriginKind.OWNER_CONTRIBUTION, ownerContribution.postingOriginKind());
    assertEquals(ownerContribution.journalEntry().lines(), ownerContribution.lines());

    assertEquals(PostingKind.STANDARD, ownerWithdrawal.postingKind());
    assertEquals(PostingOriginKind.OWNER_WITHDRAWAL, ownerWithdrawal.postingOriginKind());
    assertEquals(ownerWithdrawal.journalEntry().lines(), ownerWithdrawal.lines());

    assertEquals(PostingKind.OPENING_BALANCE, openingPosition.postingKind());
    assertEquals(PostingOriginKind.OPENING_POSITION, openingPosition.postingOriginKind());
    assertEquals(openingPosition.journalEntry().lines(), openingPosition.lines());

    assertEquals(PostingKind.STANDARD, reversal.postingKind());
    assertEquals(PostingOriginKind.REVERSAL, reversal.postingOriginKind());
    assertEquals(reversal.reversal(), reversal.postingLineage());
  }

  @Test
  void constructors_rejectNullRequiredFields() {
    assertThrows(
        NullPointerException.class, () -> new BookkeepingEntry.DirectJournal(nullOf(), null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.ExpenseSettled(
                nullOf(),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-25"),
                nullOf(),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    assertThrows(
        NullPointerException.class,
        () ->
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-25"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                nullOf(),
                null));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.OpeningPosition(nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.OpeningPosition(LocalDate.parse("2026-04-25"), nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new BookkeepingEntry.OpeningPosition(LocalDate.parse("2026-04-25"), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.Reversal(LocalDate.parse("2026-04-25"), nullOf(), null, null));
  }

  @Test
  void constructors_rejectEmptyOpeningPositionsAndNonPositiveOpeningBalances() {
    IllegalArgumentException nonPositiveOpeningBalance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "0"),
                    null));
    assertEquals("amount must carry one positive amount.", nonPositiveOpeningBalance.getMessage());
  }

  @Test
  void reversalSurface_coversUnresolvedConstructorsInterfaceDispatchAndDateMismatch() {
    JournalEntry resolvedJournal = journalEntry();
    BookkeepingEntry.Reversal unresolvedWithoutForeignExchange =
        new BookkeepingEntry.Reversal(
            resolvedJournal.effectiveDate(),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-1")),
                new ReversalReason("operator reversal")),
            null,
            null);
    BookkeepingEntry.Reversal unresolvedWithForeignExchange =
        new BookkeepingEntry.Reversal(
            resolvedJournal.effectiveDate(),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-2")),
                new ReversalReason("operator reversal")),
            null,
            null);
    BookkeepingEntry directJournal = new BookkeepingEntry.DirectJournal(resolvedJournal, null);
    BookkeepingEntry reversal =
        new BookkeepingEntry.Reversal(
            resolvedJournal.effectiveDate(),
            new PostingLineage.Reversal(
                new ReversalReference(new PostingId("posting-3")),
                new ReversalReason("operator reversal")),
            null,
            resolvedJournal);

    assertNull(unresolvedWithoutForeignExchange.foreignExchangeDetails());
    assertNull(unresolvedWithForeignExchange.foreignExchangeDetails());
    assertThrows(IllegalStateException.class, unresolvedWithoutForeignExchange::journalEntry);
    assertThrows(IllegalStateException.class, unresolvedWithForeignExchange::journalEntry);
    assertSame(resolvedJournal, directJournal.journalEntry());
    assertSame(resolvedJournal, reversal.journalEntry());
    assertSame(resolvedJournal.lines(), reversal.lines());

    IllegalArgumentException mismatchedEffectiveDate =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookkeepingEntry.Reversal(
                    resolvedJournal.effectiveDate().plusDays(1),
                    new PostingLineage.Reversal(
                        new ReversalReference(new PostingId("posting-4")),
                        new ReversalReason("operator reversal")),
                    null,
                    resolvedJournal));
    assertEquals(
        "resolvedJournalEntry effectiveDate must match reversal effectiveDate.",
        mismatchedEffectiveDate.getMessage());
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
