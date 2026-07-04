package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliFuzzTypedEntryAccountDeclarationsTest {
  @Test
  void declaration_dispatchers_reject_unsupported_entry_families() {
    IllegalArgumentException outerDispatcherFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliFuzzTypedEntryAccountDeclarations.declare(
                    new BookkeepingEntry.DirectJournal(
                        new JournalEntry(
                            LocalDate.parse("2026-04-22"),
                            List.of(
                                new JournalLine(
                                    new AccountCode("1000"),
                                    JournalLine.EntrySide.DEBIT,
                                    Money.parse("EUR", "10.00")),
                                new JournalLine(
                                    new AccountCode("2000"),
                                    JournalLine.EntrySide.CREDIT,
                                    Money.parse("EUR", "10.00")))),
                        null)));
    assertEquals(
        "typed entry declarations do not support: DirectJournal",
        outerDispatcherFailure.getMessage());

    IllegalArgumentException salesDispatcherFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliFuzzTypedEntryAccountDeclarations.salesAndExpenseDeclarations(
                    new BookkeepingEntry.OwnerContribution(
                        LocalDate.parse("2026-04-23"),
                        new AccountCode("1000"),
                        new AccountCode("3100"),
                        new MonetaryAmount("EUR", "500"),
                        null)));
    assertEquals(
        "sales-and-expenses declarations do not support: OwnerContribution",
        salesDispatcherFailure.getMessage());

    IllegalArgumentException settlementDispatcherFailure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                CliFuzzTypedEntryAccountDeclarations.settlementAndEquityDeclarations(
                    new BookkeepingEntry.SaleSettled(
                        LocalDate.parse("2026-04-24"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "500"),
                        null,
                        null,
                        null,
                        null)));
    assertEquals(
        "settlement-and-equity declarations do not support: SaleSettled",
        settlementDispatcherFailure.getMessage());
  }
}
