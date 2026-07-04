package dev.erst.fingrind.executor.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Direct coverage for caller-authored fingerprint canonicalization across retained entry kinds. */
class RequestFingerprintCallerAuthoredEntryWriterTest {
  @Test
  void append_coversCreditSettlementAndPatternOnlyEntryVariants() {
    String directJournalCanonical =
        canonical(
            new BookkeepingEntry.DirectJournal(
                new JournalEntry(
                    LocalDate.parse("2026-04-07"),
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            dev.erst.fingrind.core.Money.ofMinorUnits(
                                dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)),
                        new JournalLine(
                            new AccountCode("4000"),
                            JournalLine.EntrySide.CREDIT,
                            dev.erst.fingrind.core.Money.ofMinorUnits(
                                dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1000)))),
                null));
    String saleOnCreditCanonical =
        canonical(
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                null,
                null,
                null));
    String expenseOnCreditCanonical =
        canonical(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "12100"),
                null,
                null));
    String receiptCanonical =
        canonical(
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1000"),
                new SettlementAdjunct(new AccountCode("5600"), new MonetaryAmount("EUR", "100"))));
    String paymentCanonical =
        canonical(
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String purchaseSettledCanonical =
        canonical(
            new BookkeepingEntry.PurchaseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String purchaseOnCreditCanonical =
        canonical(
            new BookkeepingEntry.PurchaseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1400"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000")));
    String expenseSettledCanonical =
        canonical(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null));
    String ownerContributionCanonical =
        canonical(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String ownerWithdrawalCanonical =
        canonical(
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));
    String openingCanonical =
        canonical(
            new BookkeepingEntry.OpeningPosition(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000")),
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        new MonetaryAmount("EUR", "1000")))));
    String reversalCanonical =
        canonical(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("posting-1")),
                    new ReversalReason("operator reversal")),
                null,
                null));

    assertContains(directJournalCanonical, "callerAuthoredEntry.entryKind=DIRECT_JOURNAL");
    assertFalse(directJournalCanonical.contains("callerAuthoredEntry.cashAccountCode="));
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.receivableAccountCode=1100");
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.revenueAccountCode=4000");
    assertContains(saleOnCreditCanonical, "callerAuthoredEntry.inventoryRelief.present=false");
    assertContains(purchaseSettledCanonical, "callerAuthoredEntry.inventoryAccountCode=1400");
    assertContains(purchaseSettledCanonical, "callerAuthoredEntry.cashAccountCode=1000");
    assertContains(purchaseOnCreditCanonical, "callerAuthoredEntry.payableAccountCode=2100");
    assertContains(expenseOnCreditCanonical, "callerAuthoredEntry.payableAccountCode=2100");
    assertContains(expenseSettledCanonical, "callerAuthoredEntry.expenseAccountCode=5000");
    assertContains(expenseSettledCanonical, "callerAuthoredEntry.cashAccountCode=1000");
    assertContains(receiptCanonical, "callerAuthoredEntry.settlementAdjunct.present=true");
    assertContains(receiptCanonical, "callerAuthoredEntry.settlementAdjunct.accountCode=5600");
    assertContains(paymentCanonical, "callerAuthoredEntry.settlementAdjunct.present=false");
    assertContains(ownerContributionCanonical, "callerAuthoredEntry.equityAccountCode=3000");
    assertContains(ownerWithdrawalCanonical, "callerAuthoredEntry.equityAccountCode=3010");
    assertFalse(openingCanonical.contains("callerAuthoredEntry.settlementAdjunct."));
    assertContains(reversalCanonical, "callerAuthoredEntry.entryKind=REVERSAL");
    assertFalse(reversalCanonical.contains("callerAuthoredEntry.amountCurrency="));
  }

  @Test
  void append_coversTradingSaleInventoryRelief() {
    String canonical =
        canonical(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new MonetaryAmount("EUR", "4100")),
                null,
                null,
                null));

    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.present=true");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.inventoryAccountCode=1400");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.costOfSalesAccountCode=5000");
    assertContains(canonical, "callerAuthoredEntry.inventoryRelief.amountMinorUnits=4100");
  }

  private static String canonical(BookkeepingEntry entry) {
    StringBuilder canonical = new StringBuilder();
    RequestFingerprintCallerAuthoredEntryWriter.append(canonical, entry);
    return canonical.toString();
  }

  private static void assertContains(String canonical, String expectedLine) {
    assertTrue(canonical.contains(expectedLine + "\n"), expectedLine);
  }
}
