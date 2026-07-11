package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Coverage tests for the typed bookkeeping-entry variants added beyond the basic public surface.
 */
class BookkeepingEntryVariantCoverageTest {
  @Test
  void creditAndSettlementEntries_publishStableKindsOriginsAndDirectJournalShapes() {
    BookkeepingEntry.SaleOnCredit saleOnCredit =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1200"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null,
            null);
    BookkeepingEntry.PurchaseSettled purchaseSettled =
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            new ResolvedInventoryAcquisition(
                Quantity.parse(0, "1"),
                new MonetaryAmount("EUR", "1000"),
                new MonetaryAmount("EUR", "1000")),
            null,
            null,
            null);
    BookkeepingEntry.PurchaseOnCredit purchaseOnCredit =
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            new ResolvedInventoryAcquisition(
                Quantity.parse(0, "1"),
                new MonetaryAmount("EUR", "1000"),
                new MonetaryAmount("EUR", "1000")),
            null,
            null,
            null);
    BookkeepingEntry.ExpenseOnCredit expenseOnCredit =
        new BookkeepingEntry.ExpenseOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("2100"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null);
    BookkeepingEntry.Receipt receipt =
        new BookkeepingEntry.Receipt(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("1200"),
            new MonetaryAmount("EUR", "1000"),
            settlementAdjunct("250"));
    BookkeepingEntry.Payment payment =
        new BookkeepingEntry.Payment(
            LocalDate.parse("2026-04-25"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            settlementAdjunct("250"));

    assertEquals(BookkeepingEntryKind.SALE_ON_CREDIT, saleOnCredit.entryKind());
    assertEquals(PostingOriginKind.SALE_ON_CREDIT, saleOnCredit.postingOriginKind());
    assertEquals(2, saleOnCredit.journalEntry().lines().size());
    assertEquals(PostingLineage.direct(), saleOnCredit.postingLineage());

    assertEquals(BookkeepingEntryKind.PURCHASE_SETTLED, purchaseSettled.entryKind());
    assertEquals(PostingOriginKind.PURCHASE_SETTLED, purchaseSettled.postingOriginKind());
    assertEquals(2, purchaseSettled.journalEntry().lines().size());

    assertEquals(BookkeepingEntryKind.PURCHASE_ON_CREDIT, purchaseOnCredit.entryKind());
    assertEquals(PostingOriginKind.PURCHASE_ON_CREDIT, purchaseOnCredit.postingOriginKind());
    assertEquals(2, purchaseOnCredit.journalEntry().lines().size());

    assertEquals(BookkeepingEntryKind.EXPENSE_ON_CREDIT, expenseOnCredit.entryKind());
    assertEquals(PostingOriginKind.EXPENSE_ON_CREDIT, expenseOnCredit.postingOriginKind());
    assertEquals(2, expenseOnCredit.journalEntry().lines().size());

    assertEquals(BookkeepingEntryKind.RECEIPT, receipt.entryKind());
    assertEquals(PostingOriginKind.RECEIPT, receipt.postingOriginKind());
    assertEquals(3, receipt.journalEntry().lines().size());
    assertEquals(750L, receipt.journalEntry().lines().getFirst().amount().money().minorUnits());
    assertEquals(250L, receipt.journalEntry().lines().get(1).amount().money().minorUnits());

    assertEquals(BookkeepingEntryKind.PAYMENT, payment.entryKind());
    assertEquals(PostingOriginKind.PAYMENT, payment.postingOriginKind());
    assertEquals(3, payment.journalEntry().lines().size());
    assertEquals(1000L, payment.journalEntry().lines().getFirst().amount().money().minorUnits());
    assertEquals(750L, payment.journalEntry().lines().get(1).amount().money().minorUnits());
    assertEquals(250L, payment.journalEntry().lines().get(2).amount().money().minorUnits());
  }

  @Test
  void purchaseEntries_requireExecutorOwnedInventoryAcquisitionBeforeJournalDerivation() {
    BookkeepingEntry.PurchaseSettled purchaseSettled =
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null);
    BookkeepingEntry.PurchaseOnCredit purchaseOnCredit =
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            new dev.erst.fingrind.contract.bookkeeping.QuantityText("1"),
            new MonetaryAmount("EUR", "1000"),
            null,
            null,
            null,
            null);

    assertEquals(
        "purchaseSettled inventory acquisition requires executor-owned quantity resolution before journalEntry() can be derived.",
        assertThrows(IllegalStateException.class, purchaseSettled::journalEntry).getMessage());
    assertEquals(
        "purchaseOnCredit inventory acquisition requires executor-owned quantity resolution before journalEntry() can be derived.",
        assertThrows(IllegalStateException.class, purchaseOnCredit::journalEntry).getMessage());
  }

  @Test
  void
      saleEntriesWithInventoryRelief_requireExecutorOwnedInventoryCostingBeforeJournalDerivation() {
    BookkeepingEntry.SaleSettled saleSettled =
        new BookkeepingEntry.SaleSettled(
            LocalDate.parse("2026-04-25"),
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
            null);
    BookkeepingEntry.SaleOnCredit saleOnCredit =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1200"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "1000"),
            new InventoryRelief(
                new AccountCode("1400"),
                new AccountCode("5000"),
                new dev.erst.fingrind.contract.bookkeeping.QuantityText("1")),
            null,
            null,
            null,
            null);

    assertEquals(
        "sale inventory relief requires executor-owned inventory costing before journalEntry() can be derived.",
        assertThrows(IllegalStateException.class, saleSettled::journalEntry).getMessage());
    assertEquals(
        "sale inventory relief requires executor-owned inventory costing before journalEntry() can be derived.",
        assertThrows(IllegalStateException.class, saleOnCredit::journalEntry).getMessage());
  }

  @Test
  void creditEntries_deriveResolvedTaxJournalsWhenTaxSelectionIsPresent() {
    TaxSelection selection = new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat"));
    BookkeepingEntry.SaleOnCredit taxedSale =
        new BookkeepingEntry.SaleOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1200"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "10000"),
            null,
            null,
            null,
            selection,
            appliedTax(
                TaxApplicationKind.OUTPUT_SALE,
                TaxInclusionMode.EXCLUSIVE,
                "10000",
                "2100",
                "12100",
                "2100"));
    BookkeepingEntry.ExpenseOnCredit taxedExpense =
        new BookkeepingEntry.ExpenseOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("5000"),
            new AccountCode("2100"),
            new MonetaryAmount("EUR", "12100"),
            null,
            selection,
            appliedTax(
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                TaxInclusionMode.INCLUSIVE,
                "10000",
                "2100",
                "12100",
                "1300"));

    assertEquals(3, taxedSale.journalEntry().lines().size());
    assertEquals(12100L, taxedSale.journalEntry().lines().getFirst().amount().money().minorUnits());
    assertEquals(3, taxedExpense.journalEntry().lines().size());
    assertEquals(
        12100L, taxedExpense.journalEntry().lines().getLast().amount().money().minorUnits());
  }

  @Test
  void purchaseEntries_deriveResolvedInventoryTaxJournalsWhenTaxSelectionIsPresent() {
    TaxSelection selection = new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat"));
    ResolvedInventoryAcquisition acquisition =
        new ResolvedInventoryAcquisition(
            Quantity.parse(0, "1"),
            new MonetaryAmount("EUR", "10000"),
            new MonetaryAmount("EUR", "10000"));
    BookkeepingEntry.PurchaseSettled selectedButUnresolvedPurchase =
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new QuantityText("1"),
            new MonetaryAmount("EUR", "10000"),
            null,
            null,
            selection,
            null);
    BookkeepingEntry.PurchaseSettled recoverablePurchase =
        new BookkeepingEntry.PurchaseSettled(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("1000"),
            new QuantityText("1"),
            new MonetaryAmount("EUR", "10000"),
            acquisition,
            null,
            selection,
            appliedTax(
                TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
                TaxInclusionMode.EXCLUSIVE,
                "10000",
                "2100",
                "12100",
                "1300"));
    BookkeepingEntry.PurchaseOnCredit nonrecoverablePurchase =
        new BookkeepingEntry.PurchaseOnCredit(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1400"),
            new AccountCode("2100"),
            new QuantityText("1"),
            new MonetaryAmount("EUR", "10000"),
            acquisition,
            null,
            selection,
            appliedTax(
                TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
                TaxInclusionMode.EXCLUSIVE,
                "10000",
                "2100",
                "12100",
                null));

    assertEquals(selection, selectedButUnresolvedPurchase.taxSelection());
    assertEquals(3, recoverablePurchase.journalEntry().lines().size());
    assertEquals(2, nonrecoverablePurchase.journalEntry().lines().size());
    assertEquals(
        12100L, recoverablePurchase.journalEntry().lines().getLast().amount().money().minorUnits());
    assertEquals(
        12100L,
        nonrecoverablePurchase.journalEntry().lines().getLast().amount().money().minorUnits());
  }

  @Test
  void settlementAdjunctSupport_splitsReceiptAndPaymentCashLinesAndRejectsZeroCashResidual() {
    SettlementAdjunct adjunct = settlementAdjunct("250");
    JournalEntry receipt =
        BookkeepingEntrySupport.receiptEntry(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("1200"),
            new MonetaryAmount("EUR", "1000"),
            adjunct);
    JournalEntry payment =
        BookkeepingEntrySupport.paymentEntry(
            LocalDate.parse("2026-04-25"),
            new AccountCode("2100"),
            new AccountCode("1000"),
            new MonetaryAmount("EUR", "1000"),
            adjunct);

    assertEquals(3, receipt.lines().size());
    assertEquals(750L, receipt.lines().getFirst().amount().money().minorUnits());
    assertEquals(3, payment.lines().size());
    assertEquals(750L, payment.lines().get(1).amount().money().minorUnits());
    assertEquals(
        2,
        BookkeepingEntrySupport.receiptEntry(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("1200"),
                new MonetaryAmount("EUR", "1000"),
                null)
            .lines()
            .size());
    assertEquals(
        2,
        BookkeepingEntrySupport.paymentEntry(
                LocalDate.parse("2026-04-25"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1000"),
                null)
            .lines()
            .size());
    assertEquals(
        "receipt must retain one positive cash line after subtracting the settlement adjunct.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.receiptEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("1000"),
                        new AccountCode("1200"),
                        new MonetaryAmount("EUR", "1000"),
                        settlementAdjunct("1000")))
            .getMessage());
    assertEquals(
        "payment must retain one positive cash line after subtracting the settlement adjunct.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.paymentEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("2100"),
                        new AccountCode("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        settlementAdjunct("1000")))
            .getMessage());
  }

  @Test
  void settlementAdjunctValidation_enforcesCurrencyAndStrictlySmallerAmount() {
    SettlementAdjunct liveAdjunct = settlementAdjunct("250");

    assertEquals(new AccountCode("1190"), liveAdjunct.accountCode());
    assertNull(
        BookkeepingEntryScalarValidationSupport.requireOptionalSettlementAdjunct(
            null, new MonetaryAmount("EUR", "1000"), "settlementAdjunct"));
    assertDoesNotThrow(
        () ->
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("1200"),
                new MonetaryAmount("EUR", "1000"),
                liveAdjunct));
    assertEquals(
        "settlementAdjunct.amount currencyCode must match the entry amount currencyCode.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookkeepingEntry.Receipt(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("1000"),
                        new AccountCode("1200"),
                        new MonetaryAmount("EUR", "1000"),
                        new SettlementAdjunct(
                            new AccountCode("1190"), new MonetaryAmount("USD", "250"))))
            .getMessage());
    assertEquals(
        "settlementAdjunct.amount must be smaller than the settlement amount so one cash line remains.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookkeepingEntry.Payment(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("2100"),
                        new AccountCode("1000"),
                        new MonetaryAmount("EUR", "1000"),
                        settlementAdjunct("1000")))
            .getMessage());
    assertEquals(
        "amount must carry one positive amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new SettlementAdjunct(new AccountCode("1190"), new MonetaryAmount("EUR", "0")))
            .getMessage());
  }

  private static SettlementAdjunct settlementAdjunct(String minorUnits) {
    return new SettlementAdjunct(new AccountCode("1190"), new MonetaryAmount("EUR", minorUnits));
  }

  private static AppliedTax appliedTax(
      TaxApplicationKind applicationKind,
      TaxInclusionMode inclusionMode,
      String taxableAmountMinorUnits,
      String taxAmountMinorUnits,
      String grossAmountMinorUnits,
      @Nullable String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode("vat"),
        new TaxCodeName("VAT"),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableAmountMinorUnits),
        new MonetaryAmount("EUR", taxAmountMinorUnits),
        new MonetaryAmount("EUR", grossAmountMinorUnits),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }
}
