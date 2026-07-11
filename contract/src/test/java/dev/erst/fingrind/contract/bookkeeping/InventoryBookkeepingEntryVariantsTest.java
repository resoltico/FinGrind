package dev.erst.fingrind.contract.bookkeeping;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Contract tests for inventory-only typed bookkeeping entries. */
class InventoryBookkeepingEntryVariantsTest {
  private static final LocalDate EFFECTIVE_DATE = LocalDate.parse("2026-04-25");
  private static final AccountCode INVENTORY = new AccountCode("1400");
  private static final AccountCode CASH = new AccountCode("1000");
  private static final AccountCode PAYABLE = new AccountCode("2100");
  private static final AccountCode WRITE_DOWN_LOSS = new AccountCode("6100");
  private static final AccountCode SHRINKAGE_LOSS = new AccountCode("6200");
  private static final AccountCode COUNT_GAIN = new AccountCode("7100");

  @Test
  void inventoryVariants_publishKindsOriginsAndResolvedJournalEntries() {
    InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled settledCapitalization =
        new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
            EFFECTIVE_DATE, INVENTORY, CASH, amount("10000"), null, null, null);
    InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit creditCapitalization =
        new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
            EFFECTIVE_DATE, INVENTORY, PAYABLE, amount("10000"), null, null, null);
    InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown =
        new InventoryBookkeepingEntryVariants.InventoryWriteDown(
            EFFECTIVE_DATE, INVENTORY, WRITE_DOWN_LOSS, amount("1000"));
    InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage =
        new InventoryBookkeepingEntryVariants.InventoryShrinkage(
            EFFECTIVE_DATE, INVENTORY, SHRINKAGE_LOSS, new QuantityText("1"), resolvedDisposal());
    InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease =
        new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
            EFFECTIVE_DATE,
            INVENTORY,
            COUNT_GAIN,
            new QuantityText("1"),
            amount("10000"),
            resolvedAcquisition());

    assertInventoryEntry(
        settledCapitalization,
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED,
        2);
    assertInventoryEntry(
        creditCapitalization,
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        2);
    assertInventoryEntry(
        writeDown,
        BookkeepingEntryKind.INVENTORY_WRITE_DOWN,
        PostingOriginKind.INVENTORY_WRITE_DOWN,
        2);
    assertInventoryEntry(
        shrinkage,
        BookkeepingEntryKind.INVENTORY_SHRINKAGE,
        PostingOriginKind.INVENTORY_SHRINKAGE,
        2);
    assertInventoryEntry(
        countIncrease,
        BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
        PostingOriginKind.INVENTORY_COUNT_INCREASE,
        2);
    assertEquals(SHRINKAGE_LOSS, shrinkage.journalEntry().lines().getFirst().accountCode());
    assertEquals(COUNT_GAIN, countIncrease.journalEntry().lines().get(1).accountCode());
  }

  @Test
  void inventoryCapitalization_taxCompositionUsesResolvedTaxFacts() {
    InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled recoverableCapitalization =
        new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
            EFFECTIVE_DATE,
            INVENTORY,
            CASH,
            amount("12100"),
            null,
            taxSelection(),
            appliedTax(TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE, new AccountCode("1300")));
    InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit nonrecoverableCapitalization =
        new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
            EFFECTIVE_DATE,
            INVENTORY,
            PAYABLE,
            amount("12100"),
            null,
            taxSelection(),
            appliedTax(TaxApplicationKind.INPUT_EXPENSE_NONRECOVERABLE, null));

    assertInventoryEntry(
        recoverableCapitalization,
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
        PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED,
        3);
    assertInventoryEntry(
        nonrecoverableCapitalization,
        BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
        2);
    assertEquals(
        new AccountCode("1300"),
        recoverableCapitalization.journalEntry().lines().get(1).accountCode());
    assertEquals(PAYABLE, nonrecoverableCapitalization.journalEntry().lines().get(1).accountCode());
  }

  private static void assertInventoryEntry(
      InventoryBookkeepingEntryVariants entry,
      BookkeepingEntryKind expectedEntryKind,
      PostingOriginKind expectedOriginKind,
      int expectedJournalLineCount) {
    assertEquals(expectedEntryKind, entry.entryKind());
    assertEquals(PostingKind.STANDARD, entry.postingKind());
    assertEquals(expectedOriginKind, entry.postingOriginKind());
    assertEquals(expectedJournalLineCount, entry.journalEntry().lines().size());
  }

  private static MonetaryAmount amount(String minorUnits) {
    return new MonetaryAmount("EUR", minorUnits);
  }

  private static ResolvedInventoryAcquisition resolvedAcquisition() {
    return new ResolvedInventoryAcquisition(
        Quantity.ofScaledUnits(0, 1), amount("10000"), amount("10000"));
  }

  private static ResolvedInventoryDisposal resolvedDisposal() {
    return new ResolvedInventoryDisposal(
        amount("1000").toMoney(), Quantity.ofScaledUnits(0, 1), amount("1000").toMoney());
  }

  private static TaxSelection taxSelection() {
    return new TaxSelection(new TaxRegistrationId("vat-lv"), new TaxCode("vat-standard"));
  }

  private static AppliedTax appliedTax(
      TaxApplicationKind applicationKind, @Nullable AccountCode taxAccountCode) {
    return new AppliedTax(
        new TaxRegistrationId("vat-lv"),
        new TaxCode("vat-standard"),
        new TaxCodeName("VAT Standard"),
        new TaxRate(210_000),
        TaxInclusionMode.INCLUSIVE,
        applicationKind,
        amount("10000"),
        amount("2100"),
        amount("12100"),
        taxAccountCode);
  }
}
