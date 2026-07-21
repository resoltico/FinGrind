package dev.erst.fingrind.executor.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
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

/** Direct coverage for workflow fact expansion over caller-authored posting variants. */
class LedgerPlanEntryFactMapperTest {
  @Test
  void entryFacts_coverCreditExpenseOwnerAndPatternVariants() {
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null,
                null,
                null)),
        "receivableAccountCode",
        "1100");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null)),
        "payableAccountCode",
        "2100");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1000"),
                null)),
        "equityAccountCode",
        "3000");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3010"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null)),
        "equityAccountCode",
        "3010");
    assertContainsGroupText(
        LedgerPlanEntryFactMapper.entryFacts(
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
                        null)))),
        "openingBalance",
        "accountCode",
        "1000");
    assertContainsGroupText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-23"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new ReversalReason("operator reversal")),
                null,
                null)),
        "reversal",
        "reason",
        "operator reversal");
  }

  @Test
  void entryFacts_coverSettlementAdjunctPresenceAndAbsence() {
    List<BookWorkflowFact> receiptFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1000"),
                new SettlementAdjunct(new AccountCode("5600"), new MonetaryAmount("EUR", "100"))));
    List<BookWorkflowFact> paymentFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null));

    assertContainsGroupText(receiptFacts, "settlementAdjunct", "accountCode", "5600");
    assertFalse(
        paymentFacts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && "settlementAdjunct".equals(group.name())));
  }

  @Test
  void entryFacts_coverTradingSaleInventoryRelief() {
    List<BookWorkflowFact> facts =
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1250"),
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5000"),
                    new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
                null,
                null,
                null,
                null));

    assertContainsGroupText(facts, "inventoryRelief", "inventoryAccountCode", "1400");
    assertContainsGroupText(facts, "inventoryRelief", "costOfSalesAccountCode", "5000");
  }

  @Test
  void entryFacts_coverDirectJournalPurchaseAndSettledExpenseVariants() {
    List<BookWorkflowFact> directJournalFacts =
        LedgerPlanEntryFactMapper.entryFacts(
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
    List<BookWorkflowFact> purchaseSettledFacts =
        LedgerPlanEntryFactMapper.entryFacts(
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
    List<BookWorkflowFact> purchaseOnCreditFacts =
        LedgerPlanEntryFactMapper.entryFacts(
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
    List<BookWorkflowFact> expenseSettledFacts =
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null));

    assertContainsText(directJournalFacts, "entryKind", "DIRECT_JOURNAL");
    assertFalse(
        directJournalFacts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && "cashAccountCode".equals(text.name())));
    assertContainsText(purchaseSettledFacts, "inventoryAccountCode", "1400");
    assertContainsText(purchaseSettledFacts, "cashAccountCode", "1000");
    assertContainsText(purchaseOnCreditFacts, "inventoryAccountCode", "1400");
    assertContainsText(purchaseOnCreditFacts, "payableAccountCode", "2100");
    assertContainsText(expenseSettledFacts, "expenseAccountCode", "5000");
    assertContainsText(expenseSettledFacts, "cashAccountCode", "1000");
  }

  @Test
  void entryFacts_coverEveryInventoryAdjustmentVariant() {
    LocalDate effectiveDate = LocalDate.parse("2026-04-07");

    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
                effectiveDate,
                new AccountCode("1400"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null)),
        "cashAccountCode",
        "1000");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
                effectiveDate,
                new AccountCode("1400"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null)),
        "payableAccountCode",
        "2100");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new InventoryBookkeepingEntryVariants.InventoryWriteDown(
                effectiveDate,
                new AccountCode("1400"),
                new AccountCode("5100"),
                new MonetaryAmount("EUR", "1000"))),
        "writeDownLossAccountCode",
        "5100");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new InventoryBookkeepingEntryVariants.InventoryShrinkage(
                effectiveDate,
                new AccountCode("1400"),
                new AccountCode("5200"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                null)),
        "shrinkageLossAccountCode",
        "5200");
    assertContainsText(
        LedgerPlanEntryFactMapper.entryFacts(
            new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
                effectiveDate,
                new AccountCode("1400"),
                new AccountCode("4900"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
                new MonetaryAmount("EUR", "1000"),
                null)),
        "countGainAccountCode",
        "4900");
    assertContainsGroupText(
        LedgerPlanEntryFactMapper.entryFacts(
            new BookkeepingEntry.OpeningPosition(
                effectiveDate,
                List.of(
                    new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                        new AccountCode("1400"),
                        JournalLine.EntrySide.DEBIT,
                        new MonetaryAmount("EUR", "1000"),
                        new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"))))),
        "openingBalance",
        "quantity",
        "1");
  }

  private static void assertContainsText(
      List<BookWorkflowFact> facts, String name, String expectedValue) {
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Text text
                        && name.equals(text.name())
                        && expectedValue.equals(text.value())),
        name + "=" + expectedValue);
  }

  private static void assertContainsGroupText(
      List<BookWorkflowFact> facts, String groupName, String factName, String expectedValue) {
    assertTrue(
        facts.stream()
            .anyMatch(
                fact ->
                    fact instanceof BookWorkflowFact.Group group
                        && groupName.equals(group.name())
                        && group.facts().stream()
                            .anyMatch(
                                nested ->
                                    nested instanceof BookWorkflowFact.Text text
                                        && factName.equals(text.name())
                                        && expectedValue.equals(text.value()))),
        groupName + "." + factName + "=" + expectedValue);
  }
}
