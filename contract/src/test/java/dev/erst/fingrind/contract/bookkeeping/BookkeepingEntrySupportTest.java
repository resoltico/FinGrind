package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PositiveMoney;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Direct branch coverage for bookkeeping-entry tax support rules. */
class BookkeepingEntrySupportTest {
  @Test
  void journalEntryDispatch_coversDirectJournalAndResolvedReversalBranches() {
    JournalEntry directJournalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-25"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "10.00")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "10.00"))));
    BookkeepingEntry.DirectJournal directJournal =
        new BookkeepingEntry.DirectJournal(directJournalEntry, null);
    BookkeepingEntry.Reversal reversal =
        new BookkeepingEntry.Reversal(
            directJournalEntry.effectiveDate(),
            new PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("operator reversal")),
            null,
            directJournalEntry);

    assertSame(directJournalEntry, BookkeepingEntrySurfaceSupport.journalEntry(directJournal));
    assertSame(directJournalEntry, BookkeepingEntrySurfaceSupport.journalEntry(reversal));
  }

  @Test
  void saleEntry_enforcesOutputSale_andTaxAccountOnlyWhenTaxIsPositive() {
    var zeroTaxSale =
        appliedTax(
            TaxApplicationKind.OUTPUT_SALE,
            TaxInclusionMode.EXCLUSIVE,
            "10000",
            "0",
            "10000",
            null);
    var wrongKind =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            TaxInclusionMode.EXCLUSIVE,
            "10000",
            "0",
            "10000",
            null);
    var missingTaxAccount =
        appliedTax(
            TaxApplicationKind.OUTPUT_SALE,
            TaxInclusionMode.EXCLUSIVE,
            "10000",
            "2100",
            "12100",
            null);

    assertEquals(
        2,
        BookkeepingEntrySupport.saleEntry(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "10000"),
                zeroTaxSale)
            .lines()
            .size());
    assertEquals(
        "Resolved sale tax must use applicationKind OUTPUT_SALE.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.saleEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        wrongKind))
            .getMessage());
    assertEquals(
        "sale appliedTax must carry taxAccountCode when taxAmount is positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.saleEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "10000"),
                        missingTaxAccount))
            .getMessage());
  }

  @Test
  void expenseEntry_andResolvedAmountValidation_coverRecoverableNonrecoverableAndInvalidKinds() {
    var recoverableZeroTax =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            TaxInclusionMode.INCLUSIVE,
            "10000",
            "0",
            "10000",
            null);
    var nonrecoverable =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE,
            TaxInclusionMode.INCLUSIVE,
            "10000",
            "1200",
            "11200",
            null);
    var recoverableMissingTaxAccount =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            TaxInclusionMode.INCLUSIVE,
            "10000",
            "2100",
            "12100",
            null);
    var wrongKind =
        appliedTax(
            TaxApplicationKind.OUTPUT_SALE,
            TaxInclusionMode.EXCLUSIVE,
            "10000",
            "0",
            "10000",
            null);

    assertEquals(
        2,
        BookkeepingEntrySupport.expenseEntry(
                LocalDate.parse("2026-04-25"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                recoverableZeroTax)
            .lines()
            .size());
    assertEquals(
        2,
        BookkeepingEntrySupport.expenseEntry(
                LocalDate.parse("2026-04-25"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                nonrecoverable)
            .lines()
            .size());
    assertEquals(
        "expense appliedTax must carry taxAccountCode when taxAmount is positive.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.expenseEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("5000"),
                        new AccountCode("1000"),
                        recoverableMissingTaxAccount))
            .getMessage());
    assertEquals(
        "Resolved expense tax cannot use applicationKind OUTPUT_SALE.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntrySupport.expenseEntry(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("5000"),
                        new AccountCode("1000"),
                        wrongKind))
            .getMessage());
  }

  @Test
  void inventoryRelief_supportsSaleJournalExpansionAndCurrencyValidation() {
    InventoryRelief inventoryRelief =
        new InventoryRelief(
            new AccountCode("1400"), new AccountCode("5100"), new MonetaryAmount("EUR", "2500"));
    InventoryRelief acceptedInventoryRelief =
        BookkeepingEntryValidationSupport.requireOptionalInventoryRelief(
            inventoryRelief, new MonetaryAmount("EUR", "10000"), "inventoryRelief");
    JournalEntry journalEntry =
        BookkeepingEntrySupport.saleEntry(
            LocalDate.parse("2026-04-25"),
            new AccountCode("1000"),
            new AccountCode("4000"),
            new MonetaryAmount("EUR", "10000"),
            inventoryRelief);

    assertSame(inventoryRelief, acceptedInventoryRelief);
    assertEquals(4, journalEntry.lines().size());
    assertEquals(new AccountCode("5100"), journalEntry.lines().get(2).accountCode());
    assertEquals(JournalLine.EntrySide.DEBIT, journalEntry.lines().get(2).side());
    assertEquals(
        PositiveMoney.of(Money.parse("EUR", "25.00")), journalEntry.lines().get(2).amount());
    assertEquals(new AccountCode("1400"), journalEntry.lines().get(3).accountCode());
    assertEquals(JournalLine.EntrySide.CREDIT, journalEntry.lines().get(3).side());
    assertEquals(
        PositiveMoney.of(Money.parse("EUR", "25.00")), journalEntry.lines().get(3).amount());
    assertEquals(
        "inventoryRelief.amount currencyCode must match the entry amount currencyCode.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireOptionalInventoryRelief(
                        new InventoryRelief(
                            new AccountCode("1400"),
                            new AccountCode("5100"),
                            new MonetaryAmount("USD", "2500")),
                        new MonetaryAmount("EUR", "10000"),
                        "inventoryRelief"))
            .getMessage());
  }

  @Test
  void inventoryRelief_requiresPositiveAmount() {
    IllegalArgumentException rejection =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new InventoryRelief(
                    new AccountCode("1400"),
                    new AccountCode("5100"),
                    new MonetaryAmount("EUR", "0")));

    assertEquals("inventoryRelief.amount must carry one positive amount.", rejection.getMessage());
  }

  @Test
  void taxSelectionState_andResolvedTaxHelper_coverFailureAndHappyBranches() {
    var selection = new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard"));
    var exclusiveSaleTax =
        appliedTax(
            TaxApplicationKind.OUTPUT_SALE,
            TaxInclusionMode.EXCLUSIVE,
            "10000",
            "2100",
            "12100",
            "2100");
    var inclusiveExpenseTax =
        appliedTax(
            TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE,
            TaxInclusionMode.INCLUSIVE,
            "10000",
            "2100",
            "12100",
            "1300");

    assertDoesNotThrow(
        () ->
            BookkeepingEntryValidationSupport.requireTaxSelectionState(
                new MonetaryAmount("EUR", "10000"),
                selection,
                null,
                TaxApplicationKind.OUTPUT_SALE));
    assertEquals(
        "appliedTax requires one matching taxSelection.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "10000"),
                        null,
                        exclusiveSaleTax,
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
    assertEquals(
        "appliedTax must match the selected taxRegistrationId and taxCode.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "10000"),
                        selection,
                        new AppliedTax(
                            new TaxRegistrationId("vat-lv"),
                            new TaxCode("vat-other"),
                            new TaxCodeName("VAT Other"),
                            new TaxRate(210_000),
                            TaxInclusionMode.EXCLUSIVE,
                            TaxApplicationKind.OUTPUT_SALE,
                            new MonetaryAmount("EUR", "10000"),
                            new MonetaryAmount("EUR", "2100"),
                            new MonetaryAmount("EUR", "12100"),
                            new AccountCode("2100")),
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
    assertEquals(
        "appliedTax must match the selected taxRegistrationId and taxCode.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "10000"),
                        selection,
                        new AppliedTax(
                            new TaxRegistrationId("vat-ee"),
                            new TaxCode("vat-standard"),
                            new TaxCodeName("VAT Standard"),
                            new TaxRate(210_000),
                            TaxInclusionMode.EXCLUSIVE,
                            TaxApplicationKind.OUTPUT_SALE,
                            new MonetaryAmount("EUR", "10000"),
                            new MonetaryAmount("EUR", "2100"),
                            new MonetaryAmount("EUR", "12100"),
                            new AccountCode("2100")),
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
    assertEquals(
        "appliedTax applicationKind is not supported by this entry kind.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "12100"),
                        selection,
                        inclusiveExpenseTax,
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
    assertEquals(
        "Inclusive tax entries must retain the operator-supplied amount as the gross amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "10000"),
                        selection,
                        inclusiveExpenseTax,
                        TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE))
            .getMessage());
    assertEquals(
        "sale tax selection requires executor-owned tax resolution before journalEntry() can be derived.",
        assertThrows(
                IllegalStateException.class,
                () -> BookkeepingEntryValidationSupport.requireResolvedAppliedTax(null, "sale"))
            .getMessage());
    assertEquals(
        "appliedTax taxableAmount currencyCode must match the entry amount currencyCode.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("USD", "10000"),
                        selection,
                        exclusiveSaleTax,
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
    assertEquals(
        "Exclusive tax entries must retain the operator-supplied amount as the taxable amount.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    BookkeepingEntryValidationSupport.requireTaxSelectionState(
                        new MonetaryAmount("EUR", "12100"),
                        selection,
                        exclusiveSaleTax,
                        TaxApplicationKind.OUTPUT_SALE))
            .getMessage());
  }

  @Test
  void typedEntryForeignExchange_requiresSpotSettlementAndExactFunctionalAmount() {
    ForeignExchangeDetails matchingSpot =
        foreignExchangeDetails(
            "USD", "10000", "EUR", "9200", ForeignExchangeTreatmentKind.SPOT_SETTLEMENT);
    ForeignExchangeDetails realizedSettlement =
        foreignExchangeDetails(
            "USD", "10000", "EUR", "9200", ForeignExchangeTreatmentKind.REALIZED_SETTLEMENT);

    assertDoesNotThrow(
        () ->
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-25"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "9200"),
                null,
                matchingSpot,
                null,
                null));
    assertEquals(
        "sale foreignExchange.treatmentKind must be SPOT_SETTLEMENT.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookkeepingEntry.SaleSettled(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("1000"),
                        new AccountCode("4000"),
                        new MonetaryAmount("EUR", "9200"),
                        null,
                        realizedSettlement,
                        null,
                        null))
            .getMessage());
    assertEquals(
        "expense foreignExchange.functionalAmount must match the entry amount exactly.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookkeepingEntry.ExpenseSettled(
                        LocalDate.parse("2026-04-25"),
                        new AccountCode("5000"),
                        new AccountCode("1000"),
                        new MonetaryAmount("EUR", "9100"),
                        matchingSpot,
                        null,
                        null))
            .getMessage());
  }

  @Test
  void directJournalForeignExchange_allowsNonSpotTreatmentsButRequiresJournalMagnitudeMatch() {
    ForeignExchangeDetails realizedSettlement =
        foreignExchangeDetails(
            "USD", "10000", "EUR", "9200", ForeignExchangeTreatmentKind.REALIZED_SETTLEMENT);

    assertDoesNotThrow(
        () ->
            new BookkeepingEntry.DirectJournal(
                new JournalEntry(
                    LocalDate.parse("2026-04-25"),
                    List.of(
                        new JournalLine(
                            new AccountCode("1000"),
                            JournalLine.EntrySide.DEBIT,
                            Money.parse("EUR", "92.00")),
                        new JournalLine(
                            new AccountCode("4000"),
                            JournalLine.EntrySide.CREDIT,
                            Money.parse("EUR", "92.00")))),
                realizedSettlement));
    assertEquals(
        "directJournal foreignExchange.functionalAmount must match the total debit and credit magnitude.",
        assertThrows(
                IllegalArgumentException.class,
                () ->
                    new BookkeepingEntry.DirectJournal(
                        new JournalEntry(
                            LocalDate.parse("2026-04-25"),
                            List.of(
                                new JournalLine(
                                    new AccountCode("1000"),
                                    JournalLine.EntrySide.DEBIT,
                                    Money.parse("EUR", "91.00")),
                                new JournalLine(
                                    new AccountCode("4000"),
                                    JournalLine.EntrySide.CREDIT,
                                    Money.parse("EUR", "91.00")))),
                        realizedSettlement))
            .getMessage());
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
        new TaxCode("vat-standard"),
        new TaxCodeName("VAT Standard"),
        new TaxRate(210_000),
        inclusionMode,
        applicationKind,
        new MonetaryAmount("EUR", taxableAmountMinorUnits),
        new MonetaryAmount("EUR", taxAmountMinorUnits),
        new MonetaryAmount("EUR", grossAmountMinorUnits),
        taxAccountCode == null ? null : new AccountCode(taxAccountCode));
  }

  private static ForeignExchangeDetails foreignExchangeDetails(
      String transactionCurrencyCode,
      String transactionAmountMinorUnits,
      String functionalCurrencyCode,
      String functionalAmountMinorUnits,
      ForeignExchangeTreatmentKind treatmentKind) {
    return new ForeignExchangeDetails(
        new MonetaryAmount(transactionCurrencyCode, transactionAmountMinorUnits),
        new MonetaryAmount(functionalCurrencyCode, functionalAmountMinorUnits),
        new QuotedExchangeRate(
            new MonetaryAmount(transactionCurrencyCode, "10000"),
            new MonetaryAmount(functionalCurrencyCode, "9200"),
            LocalDate.parse("2026-04-24"),
            "ecb-spot"),
        treatmentKind);
  }
}
