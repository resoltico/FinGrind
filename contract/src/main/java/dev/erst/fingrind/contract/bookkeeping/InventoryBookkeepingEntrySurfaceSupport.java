package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingOriginKind;

/** Entry-surface behavior owned by the inventory bookkeeping-entry variants. */
final class InventoryBookkeepingEntrySurfaceSupport {
  private InventoryBookkeepingEntrySurfaceSupport() {}

  static BookkeepingEntryKind entryKind(InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled _ ->
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED;
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit _ ->
          BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT;
      case InventoryBookkeepingEntryVariants.InventoryWriteDown _ ->
          BookkeepingEntryKind.INVENTORY_WRITE_DOWN;
      case InventoryBookkeepingEntryVariants.InventoryShrinkage _ ->
          BookkeepingEntryKind.INVENTORY_SHRINKAGE;
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease _ ->
          BookkeepingEntryKind.INVENTORY_COUNT_INCREASE;
    };
  }

  static PostingOriginKind postingOriginKind(InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled _ ->
          PostingOriginKind.INVENTORY_CAPITALIZATION_SETTLED;
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit _ ->
          PostingOriginKind.INVENTORY_CAPITALIZATION_ON_CREDIT;
      case InventoryBookkeepingEntryVariants.InventoryWriteDown _ ->
          PostingOriginKind.INVENTORY_WRITE_DOWN;
      case InventoryBookkeepingEntryVariants.InventoryShrinkage _ ->
          PostingOriginKind.INVENTORY_SHRINKAGE;
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease _ ->
          PostingOriginKind.INVENTORY_COUNT_INCREASE;
    };
  }

  static JournalEntry journalEntry(InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          capitalizationJournalEntry(capitalization);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          capitalizationJournalEntry(capitalization);
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          BookkeepingEntrySupport.pairedEntry(
              writeDown.effectiveDate(),
              writeDown.writeDownLossAccountCode(),
              writeDown.inventoryAccountCode(),
              writeDown.amount());
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          BookkeepingEntrySupport.pairedEntry(
              shrinkage.effectiveDate(),
              shrinkage.shrinkageLossAccountCode(),
              shrinkage.inventoryAccountCode(),
              MonetaryAmount.of(
                  BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryDisposal(
                          shrinkage.resolvedInventoryDisposal(), "inventoryShrinkage")
                      .carryingCost()));
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          BookkeepingEntrySupport.pairedEntry(
              countIncrease.effectiveDate(),
              countIncrease.inventoryAccountCode(),
              countIncrease.countGainAccountCode(),
              BookkeepingEntryInventoryValidationSupport.requireResolvedInventoryAcquisition(
                      countIncrease.resolvedInventoryAcquisition(), "inventoryCountIncrease")
                  .carryingCost());
    };
  }

  private static JournalEntry capitalizationJournalEntry(
      InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.inventoryAccountCode(),
          entry.cashAccountCode(),
          entry.amount());
    }
    return BookkeepingEntrySupport.inventoryCostEntry(
        entry.effectiveDate(),
        entry.inventoryAccountCode(),
        entry.cashAccountCode(),
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "inventoryCapitalizationSettled"));
  }

  private static JournalEntry capitalizationJournalEntry(
      InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit entry) {
    if (entry.taxSelection() == null) {
      return BookkeepingEntrySupport.pairedEntry(
          entry.effectiveDate(),
          entry.inventoryAccountCode(),
          entry.payableAccountCode(),
          entry.amount());
    }
    return BookkeepingEntrySupport.inventoryCostEntry(
        entry.effectiveDate(),
        entry.inventoryAccountCode(),
        entry.payableAccountCode(),
        BookkeepingEntryTaxValidationSupport.requireResolvedAppliedTax(
            entry.appliedTax(), "inventoryCapitalizationOnCredit"));
  }
}
