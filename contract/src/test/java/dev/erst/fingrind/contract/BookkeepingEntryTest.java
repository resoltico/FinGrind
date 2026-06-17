package dev.erst.fingrind.contract;

import static dev.erst.fingrind.contract.NullTestSupport.nullOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipe;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
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
    BookkeepingEntry.Journal cashRevenue =
        BookkeepingEntry.cashRevenue(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.Journal cashExpense =
        BookkeepingEntry.cashExpense(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.Journal equityContribution =
        BookkeepingEntry.equityContribution(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("3000"),
            new MonetaryAmount("EUR", "1000"));
    BookkeepingEntry.Journal equityWithdrawal =
        BookkeepingEntry.equityWithdrawal(
            LocalDate.parse("2026-04-25"),
            new AccountCode("3010"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"));

    assertEquals(BookkeepingEntryKind.JOURNAL, cashRevenue.entryKind());
    assertEquals(JournalRecipeKind.CASH_REVENUE, requiredRecipe(cashRevenue).recipeKind());
    assertEquals(cashRevenue.journalEntry().lines(), cashRevenue.lines());
    assertEquals(BookkeepingEntryKind.JOURNAL, cashExpense.entryKind());
    assertEquals(JournalRecipeKind.CASH_EXPENSE, requiredRecipe(cashExpense).recipeKind());
    assertEquals(BookkeepingEntryKind.JOURNAL, equityContribution.entryKind());
    assertEquals(
        JournalRecipeKind.EQUITY_CONTRIBUTION, requiredRecipe(equityContribution).recipeKind());
    assertEquals(BookkeepingEntryKind.JOURNAL, equityWithdrawal.entryKind());
    assertEquals(
        JournalRecipeKind.EQUITY_WITHDRAWAL, requiredRecipe(equityWithdrawal).recipeKind());
    assertEquals(LocalDate.parse("2026-04-25"), equityWithdrawal.effectiveDate());

    IllegalArgumentException nonPositiveAmount =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                BookkeepingEntry.cashRevenue(
                    LocalDate.parse("2026-04-25"),
                    new AccountCode("1000"),
                    new AccountCode("4000"),
                    new MonetaryAmount("EUR", "0")));
    assertEquals("amount must carry one positive amount.", nonPositiveAmount.getMessage());
  }

  @Test
  void administrativeAdjustments_exposeEffectiveDateLinesAndEntryKinds() {
    BookkeepingEntry.Journal directJournal = new BookkeepingEntry.Journal(journalEntry(), null);
    BookkeepingEntry.OpenAccountingPosition openingAccountingPosition =
        new BookkeepingEntry.OpenAccountingPosition(
            LocalDate.parse("2026-04-07"),
            List.of(
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "1000")),
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    new AccountCode("3000"),
                    JournalLine.EntrySide.CREDIT,
                    new MonetaryAmount("EUR", "1000"))));
    BookkeepingEntry.ReversalAdjustment reversalAdjustment =
        new BookkeepingEntry.ReversalAdjustment(
            journalEntry(),
            new PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")));

    assertEquals(BookkeepingEntryKind.JOURNAL, directJournal.entryKind());
    assertEquals(journalEntry().lines(), directJournal.lines());
    assertEquals(
        BookkeepingEntryKind.OPEN_ACCOUNTING_POSITION, openingAccountingPosition.entryKind());
    assertEquals(BookkeepingEntryKind.REVERSAL_ADJUSTMENT, reversalAdjustment.entryKind());
    assertEquals(LocalDate.parse("2026-04-07"), openingAccountingPosition.effectiveDate());
    assertEquals(LocalDate.parse("2026-04-07"), reversalAdjustment.effectiveDate());
    assertEquals(2, openingAccountingPosition.lines().size());
    assertEquals(journalEntry().lines(), reversalAdjustment.lines());
  }

  @Test
  void constructors_rejectNullRequiredFields() {
    assertThrows(
        NullPointerException.class,
        () ->
            BookkeepingEntry.cashExpense(
                nullOf(),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000")));
    assertThrows(
        NullPointerException.class,
        () ->
            BookkeepingEntry.equityContribution(
                LocalDate.parse("2026-04-25"),
                nullOf(),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000")));
    assertThrows(
        NullPointerException.class,
        () ->
            BookkeepingEntry.equityWithdrawal(
                LocalDate.parse("2026-04-25"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                nullOf()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.OpenAccountingPosition(nullOf(), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.OpenAccountingPosition(LocalDate.parse("2026-04-25"), nullOf()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new BookkeepingEntry.OpenAccountingPosition(LocalDate.parse("2026-04-25"), List.of()));
    assertThrows(
        NullPointerException.class,
        () -> new BookkeepingEntry.ReversalAdjustment(journalEntry(), nullOf()));
  }

  @Test
  void constructors_rejectRecipeMismatchesAndNonPositiveOpeningBalances() {
    JournalRecipe.CashRevenue recipe =
        new JournalRecipe.CashRevenue(
            new AccountCode("1000"), new AccountCode("4000"), new MonetaryAmount("EUR", "1000"));
    JournalEntry mismatchedJournal =
        new JournalEntry(
            LocalDate.parse("2026-04-25"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("4100"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00"))));

    IllegalArgumentException mismatchedRecipe =
        assertThrows(
            IllegalArgumentException.class,
            () -> new BookkeepingEntry.Journal(mismatchedJournal, recipe));
    assertEquals(
        "journalEntry must equal the journal derived from the selected recipe.",
        mismatchedRecipe.getMessage());

    IllegalArgumentException nonPositiveOpeningBalance =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    new MonetaryAmount("EUR", "0")));
    assertEquals("amount must carry one positive amount.", nonPositiveOpeningBalance.getMessage());
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

  private static JournalRecipe requiredRecipe(BookkeepingEntry.Journal journal) {
    return assertInstanceOf(JournalRecipe.class, journal.recipe());
  }
}
