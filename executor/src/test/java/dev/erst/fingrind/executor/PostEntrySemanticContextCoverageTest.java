package dev.erst.fingrind.executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for account-reference extraction across caller-authored entry variants. */
class PostEntrySemanticContextCoverageTest {
  @Test
  void from_coversDirectJournalPurchaseInventoryAndReversalVariants() {
    PostEntrySemanticContext directJournalContext =
        context(
            new BookkeepingEntry.DirectJournal(
                journalEntry(
                    "1000", JournalLine.EntrySide.DEBIT, "4000", JournalLine.EntrySide.CREDIT),
                null));
    PostEntrySemanticContext saleContext =
        context(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1000"),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                null,
                null,
                null,
                null));
    PostEntrySemanticContext purchaseSettledContext =
        context(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null));
    PostEntrySemanticContext purchaseOnCreditContext =
        context(
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null));
    PostEntrySemanticContext openingContext =
        context(
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
                        null))));
    PostEntrySemanticContext unresolvedReversalContext =
        context(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("posting-unresolved")),
                    new ReversalReason("operator correction")),
                null,
                null));
    PostEntrySemanticContext resolvedReversalContext =
        context(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("posting-resolved")),
                    new ReversalReason("operator correction")),
                null,
                journalEntry(
                    "1000", JournalLine.EntrySide.DEBIT, "4000", JournalLine.EntrySide.CREDIT)));

    assertEquals("entryKind", directJournalContext.selectorField());
    assertEquals("DIRECT_JOURNAL", directJournalContext.selectorValue());
    assertEquals(
        List.of(new AccountCode("1000"), new AccountCode("4000")),
        List.copyOf(directJournalContext.referencedAccounts()));
    assertEquals(
        List.of(
            new AccountCode("1000"),
            new AccountCode("4000"),
            new AccountCode("1400"),
            new AccountCode("5000")),
        List.copyOf(saleContext.referencedAccounts()));
    assertEquals(
        List.of(new AccountCode("1400"), new AccountCode("1000")),
        List.copyOf(purchaseSettledContext.referencedAccounts()));
    assertEquals(
        List.of(new AccountCode("1400"), new AccountCode("2100")),
        List.copyOf(purchaseOnCreditContext.referencedAccounts()));
    assertEquals("purchase-receipt", purchaseSettledContext.sourceDocumentTypes().scaffoldValue());
    assertEquals(
        List.of(new AccountCode("1000"), new AccountCode("3000")),
        List.copyOf(openingContext.referencedAccounts()));
    assertEquals(List.of(), List.copyOf(unresolvedReversalContext.referencedAccounts()));
    assertEquals(
        List.of(new AccountCode("1000"), new AccountCode("4000")),
        List.copyOf(resolvedReversalContext.referencedAccounts()));
  }

  private static PostEntrySemanticContext context(BookkeepingEntry entry) {
    return PostEntrySemanticContext.from(entry, ProtocolCatalog.domain().requestSurface());
  }

  private static JournalEntry journalEntry(
      String debitAccountCode,
      JournalLine.EntrySide debitSide,
      String creditAccountCode,
      JournalLine.EntrySide creditSide) {
    return new JournalEntry(
        LocalDate.parse("2026-04-07"),
        List.of(
            new JournalLine(
                new AccountCode(debitAccountCode),
                debitSide,
                dev.erst.fingrind.core.Money.ofMinorUnits(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)),
            new JournalLine(
                new AccountCode(creditAccountCode),
                creditSide,
                dev.erst.fingrind.core.Money.ofMinorUnits(
                    dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000))));
  }
}
