package dev.erst.fingrind.sqlite;

import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;

/** Maps inventory-specific originating-entry facts onto the shared posting-fact value shape. */
final class SqliteInventoryOriginatingEntryFactValues {
  private SqliteInventoryOriginatingEntryFactValues() {}

  static SqliteOriginatingEntryFactMapper.OriginatingEntryFactValues originatingEntryFactValues(
      InventoryBookkeepingEntryVariants entry) {
    return switch (entry) {
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              capitalization.inventoryAccountCode().value(),
              capitalization.cashAccountCode().value(),
              capitalization.amount(),
              null);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              capitalization.inventoryAccountCode().value(),
              capitalization.payableAccountCode().value(),
              capitalization.amount(),
              null);
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          SqliteOriginatingEntryFactMapper.simpleOriginatingEntryFactValues(
              writeDown.writeDownLossAccountCode().value(),
              writeDown.inventoryAccountCode().value(),
              writeDown.amount(),
              null);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          SqliteOriginatingEntryFactMapper.quantityOnlyOriginatingEntryFactValues(
              shrinkage.shrinkageLossAccountCode().value(),
              shrinkage.inventoryAccountCode().value(),
              shrinkage.quantity().value());
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          SqliteOriginatingEntryFactMapper.purchaseOriginatingEntryFactValues(
              countIncrease.inventoryAccountCode().value(),
              countIncrease.countGainAccountCode().value(),
              countIncrease.quantity().value(),
              countIncrease.unitCost());
    };
  }
}
