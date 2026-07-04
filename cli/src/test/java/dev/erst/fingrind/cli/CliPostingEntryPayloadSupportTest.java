package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.cli.json.CliBookQueryJsonModels;
import dev.erst.fingrind.cli.json.CliForeignExchangeJsonModels;
import dev.erst.fingrind.cli.json.CliPostingEntryPayload;
import dev.erst.fingrind.cli.json.CliTaxJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.bookkeeping.SettlementAdjunct;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.fx.ForeignExchangeTreatmentKind;
import dev.erst.fingrind.contract.fx.QuotedExchangeRate;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxSelection;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** Focused coverage for caller-authored posting entry payload mapping and text rendering. */
class CliPostingEntryPayloadSupportTest {
  @Test
  void entryPayload_mapsEverySupportedEntryVariant() {
    assertNull(CliPostingEntryPayloadSupport.entryPayload(null));

    CliPostingEntryPayload directJournalPayload =
        CliPostingEntryPayloadSupport.entryPayload(directJournalEntry());
    assertNotNull(directJournalPayload);
    assertEquals("DIRECT_JOURNAL", directJournalPayload.entryKind());
    assertNull(directJournalPayload.amount());
    assertNull(directJournalPayload.reversal());
    assertNull(directJournalPayload.openingBalances());

    CliPostingEntryPayload salePayload = CliPostingEntryPayloadSupport.entryPayload(saleEntry());
    assertNotNull(salePayload);
    assertEquals("SALE_SETTLED", salePayload.entryKind());
    assertEquals("1000", salePayload.cashAccountCode());
    assertEquals("4000", salePayload.revenueAccountCode());

    CliPostingEntryPayload purchaseSettledPayload =
        CliPostingEntryPayloadSupport.entryPayload(purchaseSettledEntry());
    assertNotNull(purchaseSettledPayload);
    assertEquals("PURCHASE_SETTLED", purchaseSettledPayload.entryKind());
    assertEquals("1000", purchaseSettledPayload.cashAccountCode());
    assertEquals("1400", purchaseSettledPayload.inventoryAccountCode());

    CliPostingEntryPayload purchaseOnCreditPayload =
        CliPostingEntryPayloadSupport.entryPayload(purchaseOnCreditEntry());
    assertNotNull(purchaseOnCreditPayload);
    assertEquals("PURCHASE_ON_CREDIT", purchaseOnCreditPayload.entryKind());
    assertEquals("2100", purchaseOnCreditPayload.payableAccountCode());
    assertEquals("1400", purchaseOnCreditPayload.inventoryAccountCode());

    CliPostingEntryPayload expensePayload =
        CliPostingEntryPayloadSupport.entryPayload(expenseEntry());
    assertNotNull(expensePayload);
    assertEquals("EXPENSE_SETTLED", expensePayload.entryKind());
    assertEquals("5000", expensePayload.expenseAccountCode());
    assertEquals("1000", expensePayload.cashAccountCode());

    CliPostingEntryPayload ownerContributionPayload =
        CliPostingEntryPayloadSupport.entryPayload(ownerContributionEntry());
    assertNotNull(ownerContributionPayload);
    assertEquals("OWNER_CONTRIBUTION", ownerContributionPayload.entryKind());
    assertEquals("1000", ownerContributionPayload.cashAccountCode());
    assertEquals("3000", ownerContributionPayload.equityAccountCode());

    CliPostingEntryPayload ownerWithdrawalPayload =
        CliPostingEntryPayloadSupport.entryPayload(ownerWithdrawalEntry());
    assertNotNull(ownerWithdrawalPayload);
    assertEquals("OWNER_WITHDRAWAL", ownerWithdrawalPayload.entryKind());
    assertEquals("1000", ownerWithdrawalPayload.cashAccountCode());
    assertEquals("3010", ownerWithdrawalPayload.equityAccountCode());

    CliPostingEntryPayload openingPositionPayload =
        CliPostingEntryPayloadSupport.entryPayload(openingPositionEntry());
    assertNotNull(openingPositionPayload);
    assertEquals("OPENING_POSITION", openingPositionPayload.entryKind());
    assertNotNull(openingPositionPayload.openingBalances());
    assertEquals(2, openingPositionPayload.openingBalances().size());
    assertEquals("1000", openingPositionPayload.openingBalances().getFirst().accountCode());
    assertEquals("DEBIT", openingPositionPayload.openingBalances().getFirst().side());

    CliPostingEntryPayload reversalPayload =
        CliPostingEntryPayloadSupport.entryPayload(reversalEntry());
    assertNotNull(reversalPayload);
    assertEquals("REVERSAL", reversalPayload.entryKind());
    assertNotNull(reversalPayload.reversal());
    assertEquals("posting-1", reversalPayload.reversal().priorPostingId());
    assertEquals("Correction", reversalPayload.reversal().reason());
  }

  @Test
  void renderEntryFacts_rendersScalarAndReversalFacts() {
    String expenseFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(expenseEntry()));
    String ownerContributionFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(ownerContributionEntry()));
    String ownerWithdrawalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(ownerWithdrawalEntry()));
    String reversalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(reversalEntry()));

    assertTrue(expenseFacts.contains("Entry kind"));
    assertTrue(expenseFacts.contains("Cash account"));
    assertTrue(expenseFacts.contains("1000"));
    assertTrue(expenseFacts.contains("Expense account"));
    assertTrue(expenseFacts.contains("5000"));
    assertTrue(expenseFacts.contains("Amount"));
    assertTrue(expenseFacts.contains("12.50"));
    assertFalse(expenseFacts.contains("Opening balances"));

    assertTrue(ownerContributionFacts.contains("Equity account"));
    assertTrue(ownerContributionFacts.contains("3000"));

    assertTrue(ownerWithdrawalFacts.contains("Equity account"));
    assertTrue(ownerWithdrawalFacts.contains("3010"));

    assertTrue(reversalFacts.contains("Prior posting id"));
    assertTrue(reversalFacts.contains("posting-1"));
    assertTrue(reversalFacts.contains("Reason"));
    assertTrue(reversalFacts.contains("Correction"));
  }

  @Test
  void renderEntryFacts_rendersAppliedTaxWithoutSeparateTaxAccount() {
    String rendered =
        CliPostingEntryPayloadSupport.renderEntryFacts(
            new CliPostingEntryPayload(
                "EXPENSE_SETTLED",
                "1000",
                NullTestSupport.nullOf(String.class),
                NullTestSupport.nullOf(String.class),
                NullTestSupport.nullOf(String.class),
                NullTestSupport.nullOf(String.class),
                "5600",
                NullTestSupport.nullOf(String.class),
                new MonetaryAmount("EUR", "11200"),
                NullTestSupport.nullOf(CliPostingEntryPayload.InventoryReliefPayload.class),
                NullTestSupport.nullOf(CliPostingEntryPayload.SettlementAdjunctPayload.class),
                NullTestSupport.nullOf(CliForeignExchangeJsonModels.ForeignExchangePayload.class),
                new CliTaxJsonModels.TaxSelectionPayload("vat-lv", "vat-nonrecoverable-expense"),
                new CliTaxJsonModels.AppliedTaxPayload(
                    "vat-lv",
                    "vat-nonrecoverable-expense",
                    "VAT Nonrecoverable Expense",
                    120_000,
                    "INCLUSIVE",
                    "INPUT_EXPENSE_NONRECOVERABLE",
                    new MonetaryAmount("EUR", "10000"),
                    new MonetaryAmount("EUR", "1200"),
                    new MonetaryAmount("EUR", "11200"),
                    NullTestSupport.nullOf(String.class)),
                NullTestSupport.nullOf(CliBookQueryJsonModels.ReversalPayload.class),
                NullTestSupport
                    .<List<dev.erst.fingrind.cli.json.CliOpeningBalancePayload>>nullOf()));

    assertTrue(rendered.contains("Resolved tax code name"));
    assertTrue(rendered.contains("VAT Nonrecoverable Expense"));
    assertTrue(rendered.contains("Tax account"));
    assertTrue(rendered.contains("(none)"));
  }

  @Test
  void renderEntryFacts_rendersOpeningBalancesOnlyWhenPresent() {
    String openingFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(openingPositionEntry()));
    String directJournalFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(
            new CliPostingEntryPayload(
                "DIRECT_JOURNAL",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                NullTestSupport.nullOf(CliPostingEntryPayload.InventoryReliefPayload.class),
                NullTestSupport.nullOf(CliPostingEntryPayload.SettlementAdjunctPayload.class),
                NullTestSupport.nullOf(CliForeignExchangeJsonModels.ForeignExchangePayload.class),
                NullTestSupport.nullOf(CliTaxJsonModels.TaxSelectionPayload.class),
                NullTestSupport.nullOf(CliTaxJsonModels.AppliedTaxPayload.class),
                NullTestSupport.nullOf(CliBookQueryJsonModels.ReversalPayload.class),
                List.of()));

    assertTrue(openingFacts.contains("Opening balances"));
    assertTrue(openingFacts.contains("1000"));
    assertTrue(openingFacts.contains("3000"));
    assertTrue(openingFacts.contains("12.50"));
    assertFalse(directJournalFacts.contains("Opening balances"));
  }

  @Test
  void entryPayload_mapsCreditAndSettlementVariantsWithOwnedNestedFacts() {
    CliPostingEntryPayload creditSalePayload = entryPayload(saleOnCreditEntry());
    CliPostingEntryPayload creditExpensePayload = entryPayload(expenseOnCreditEntry());
    CliPostingEntryPayload receiptPayload = entryPayload(receiptEntry());
    CliPostingEntryPayload paymentPayload = entryPayload(paymentEntry());
    var creditSaleTaxSelection = Objects.requireNonNull(creditSalePayload.taxSelection());
    var creditSaleAppliedTax = Objects.requireNonNull(creditSalePayload.appliedTax());
    var creditExpenseTaxSelection = Objects.requireNonNull(creditExpensePayload.taxSelection());
    var creditExpenseAppliedTax = Objects.requireNonNull(creditExpensePayload.appliedTax());
    var receiptAdjunct = Objects.requireNonNull(receiptPayload.settlementAdjunct());
    var paymentAdjunct = Objects.requireNonNull(paymentPayload.settlementAdjunct());

    assertEquals("1100", creditSalePayload.receivableAccountCode());
    assertEquals("vat-standard-sale", creditSaleTaxSelection.taxCode());
    assertEquals("vat-standard-sale", creditSaleAppliedTax.taxCode());
    assertEquals("2100", creditExpensePayload.payableAccountCode());
    assertEquals("vat-standard-expense", creditExpenseTaxSelection.taxCode());
    assertEquals("vat-standard-expense", creditExpenseAppliedTax.taxCode());
    assertEquals("6100", receiptAdjunct.accountCode());
    assertEquals("50", receiptAdjunct.amount().minorUnits());
    assertEquals("6200", paymentAdjunct.accountCode());
    assertEquals("75", paymentAdjunct.amount().minorUnits());
  }

  @Test
  void renderEntryFacts_rendersSettlementAdjunctAndTaxSelectionFacts() {
    String creditSaleFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(saleOnCreditEntry()));
    String receiptFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(receiptEntry()));
    String paymentFacts =
        CliPostingEntryPayloadSupport.renderEntryFacts(entryPayload(paymentEntry()));

    assertTrue(creditSaleFacts.contains("Receivable account"));
    assertTrue(creditSaleFacts.contains("Tax registration id"));
    assertTrue(creditSaleFacts.contains("vat-lv"));
    assertTrue(creditSaleFacts.contains("Tax code"));
    assertTrue(creditSaleFacts.contains("vat-standard-sale"));
    assertTrue(receiptFacts.contains("Settlement adjunct account"));
    assertTrue(receiptFacts.contains("6100"));
    assertTrue(receiptFacts.contains("Settlement adjunct amount"));
    assertTrue(receiptFacts.contains("0.50"));
    assertTrue(paymentFacts.contains("Settlement adjunct account"));
    assertTrue(paymentFacts.contains("6200"));
    assertTrue(paymentFacts.contains("Settlement adjunct amount"));
    assertTrue(paymentFacts.contains("0.75"));
  }

  @Test
  void renderEntryFacts_rendersInventoryReliefFacts() {
    CliPostingEntryPayload payload = entryPayload(saleEntryWithInventoryRelief());

    assertNotNull(payload.inventoryRelief());
    String rendered = CliPostingEntryPayloadSupport.renderEntryFacts(payload);

    assertTrue(rendered.contains("Inventory account"));
    assertTrue(rendered.contains("1400"));
    assertTrue(rendered.contains("Cost of sales account"));
    assertTrue(rendered.contains("5000"));
    assertTrue(rendered.contains("Inventory relief amount"));
    assertTrue(rendered.contains("4.00"));
  }

  @Test
  void entryPayload_preservesNullOptionalTaxAndSettlementFacts() {
    CliPostingEntryPayload creditSalePayload =
        entryPayload(
            new BookkeepingEntry.SaleOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1100"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1000"),
                null,
                null,
                null));
    CliPostingEntryPayload creditExpensePayload =
        entryPayload(
            new BookkeepingEntry.ExpenseOnCredit(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("2100"),
                new MonetaryAmount("EUR", "1210"),
                null,
                null));
    CliPostingEntryPayload receiptPayload =
        entryPayload(
            new BookkeepingEntry.Receipt(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("1100"),
                new MonetaryAmount("EUR", "1250"),
                null));
    CliPostingEntryPayload paymentPayload =
        entryPayload(
            new BookkeepingEntry.Payment(
                LocalDate.parse("2026-04-07"),
                new AccountCode("2100"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250"),
                null));

    assertNull(creditSalePayload.taxSelection());
    assertNull(creditSalePayload.appliedTax());
    assertNull(creditExpensePayload.taxSelection());
    assertNull(creditExpensePayload.appliedTax());
    assertNull(receiptPayload.settlementAdjunct());
    assertNull(paymentPayload.settlementAdjunct());
  }

  @Test
  void entryPayload_andRenderedFacts_includeOwnedForeignExchangeDetails() {
    CliPostingEntryPayload payload =
        entryPayload(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                null,
                foreignExchangeDetails(),
                null,
                null));

    assertNotNull(payload.foreignExchange());
    assertEquals("USD", payload.foreignExchange().transactionAmount().currencyCode());
    assertEquals("9200", payload.foreignExchange().functionalAmount().minorUnits());
    assertEquals("SPOT_SETTLEMENT", payload.foreignExchange().treatmentKind());

    String rendered = CliPostingEntryPayloadSupport.renderEntryFacts(payload);
    assertTrue(rendered.contains("Transaction amount"));
    assertTrue(rendered.contains("USD 100.00"));
    assertTrue(rendered.contains("Functional amount"));
    assertTrue(rendered.contains("EUR 92.00"));
    assertTrue(rendered.contains("FX treatment"));
    assertTrue(rendered.contains("Quote source"));
    assertTrue(rendered.contains("ecb-spot"));
  }

  private static BookkeepingEntry directJournalEntry() {
    return new BookkeepingEntry.DirectJournal(
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("12.50")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("12.50")))),
        null);
  }

  private static BookkeepingEntry saleEntry() {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1250"),
        null,
        null,
        null,
        null);
  }

  private static BookkeepingEntry saleEntryWithInventoryRelief() {
    return new BookkeepingEntry.SaleSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1250"),
        new InventoryRelief(
            new AccountCode("1400"), new AccountCode("5000"), new MonetaryAmount("EUR", "400")),
        null,
        null,
        null);
  }

  private static BookkeepingEntry expenseEntry() {
    return new BookkeepingEntry.ExpenseSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("5000"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        null,
        null,
        null);
  }

  private static BookkeepingEntry purchaseSettledEntry() {
    return new BookkeepingEntry.PurchaseSettled(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1400"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        null);
  }

  private static BookkeepingEntry purchaseOnCreditEntry() {
    return new BookkeepingEntry.PurchaseOnCredit(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1400"),
        new AccountCode("2100"),
        new MonetaryAmount("EUR", "1250"));
  }

  private static BookkeepingEntry saleOnCreditEntry() {
    return new BookkeepingEntry.SaleOnCredit(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1100"),
        new AccountCode("4000"),
        new MonetaryAmount("EUR", "1000"),
        null,
        taxSelection("vat-standard-sale"),
        appliedSaleTax("vat-standard-sale", "2100"));
  }

  private static BookkeepingEntry expenseOnCreditEntry() {
    return new BookkeepingEntry.ExpenseOnCredit(
        LocalDate.parse("2026-04-07"),
        new AccountCode("5000"),
        new AccountCode("2100"),
        new MonetaryAmount("EUR", "1210"),
        taxSelection("vat-standard-expense"),
        appliedExpenseTax("vat-standard-expense", "1300"));
  }

  private static BookkeepingEntry receiptEntry() {
    return new BookkeepingEntry.Receipt(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("1100"),
        new MonetaryAmount("EUR", "1250"),
        new SettlementAdjunct(new AccountCode("6100"), new MonetaryAmount("EUR", "50")));
  }

  private static BookkeepingEntry paymentEntry() {
    return new BookkeepingEntry.Payment(
        LocalDate.parse("2026-04-07"),
        new AccountCode("2100"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        new SettlementAdjunct(new AccountCode("6200"), new MonetaryAmount("EUR", "75")));
  }

  private static BookkeepingEntry ownerContributionEntry() {
    return new BookkeepingEntry.OwnerContribution(
        LocalDate.parse("2026-04-07"),
        new AccountCode("1000"),
        new AccountCode("3000"),
        new MonetaryAmount("EUR", "1250"),
        null);
  }

  private static BookkeepingEntry ownerWithdrawalEntry() {
    return new BookkeepingEntry.OwnerWithdrawal(
        LocalDate.parse("2026-04-07"),
        new AccountCode("3010"),
        new AccountCode("1000"),
        new MonetaryAmount("EUR", "1250"),
        null);
  }

  private static BookkeepingEntry openingPositionEntry() {
    return new BookkeepingEntry.OpeningPosition(
        LocalDate.parse("2026-04-07"),
        List.of(
            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                new AccountCode("1000"),
                JournalLine.EntrySide.DEBIT,
                new MonetaryAmount("EUR", "1250")),
            new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
                new AccountCode("3000"),
                JournalLine.EntrySide.CREDIT,
                new MonetaryAmount("EUR", "1250"))));
  }

  private static BookkeepingEntry reversalEntry() {
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"), JournalLine.EntrySide.DEBIT, money("12.50")),
                new JournalLine(
                    new AccountCode("2000"), JournalLine.EntrySide.CREDIT, money("12.50"))));
    return new BookkeepingEntry.Reversal(
        journalEntry.effectiveDate(),
        new PostingLineage.Reversal(
            new ReversalReference(new PostingId("posting-1")), new ReversalReason("Correction")),
        null,
        journalEntry);
  }

  private static dev.erst.fingrind.core.Money money(String amount) {
    return dev.erst.fingrind.core.Money.parse("EUR", amount);
  }

  private static CliPostingEntryPayload entryPayload(BookkeepingEntry entry) {
    return Objects.requireNonNull(CliPostingEntryPayloadSupport.entryPayload(entry));
  }

  private static ForeignExchangeDetails foreignExchangeDetails() {
    return new ForeignExchangeDetails(
        new MonetaryAmount("USD", "10000"),
        new MonetaryAmount("EUR", "9200"),
        new QuotedExchangeRate(
            new MonetaryAmount("USD", "10000"),
            new MonetaryAmount("EUR", "9200"),
            LocalDate.parse("2026-04-06"),
            "ecb-spot"),
        ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
  }

  private static TaxSelection taxSelection(String taxCode) {
    return new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode(taxCode));
  }

  private static AppliedTax appliedSaleTax(String taxCode, String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT Standard Sale"),
        new TaxRate(210_000),
        TaxInclusionMode.EXCLUSIVE,
        TaxApplicationKind.OUTPUT_SALE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(taxAccountCode));
  }

  private static AppliedTax appliedExpenseTax(String taxCode, String taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode(taxCode),
        new TaxCodeName("VAT Standard Expense"),
        new TaxRate(210_000),
        TaxInclusionMode.INCLUSIVE,
        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
        new MonetaryAmount("EUR", "1000"),
        new MonetaryAmount("EUR", "210"),
        new MonetaryAmount("EUR", "1210"),
        new AccountCode(taxAccountCode));
  }
}
