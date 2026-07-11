package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.protocol.ProtocolInventoryPostingRequestFieldSets;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.core.AccountCode;
import tools.jackson.databind.node.ObjectNode;

/** Reads typed inventory acquisition and maintenance request payloads. */
final class CliInventoryBookkeepingEntryReaders {
  private CliInventoryBookkeepingEntryReaders() {}

  static BookkeepingEntry.PurchaseSettled readPurchaseSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolInventoryPostingRequestFieldSets.purchaseSettledFields());
    return new BookkeepingEntry.PurchaseSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveQuantity(rootNode),
        CliBookkeepingEntryStructureParser.requiredPositiveUnitCost(rootNode),
        null,
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  static BookkeepingEntry.PurchaseOnCredit readPurchaseOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolInventoryPostingRequestFieldSets.purchaseOnCreditFields());
    return new BookkeepingEntry.PurchaseOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveQuantity(rootNode),
        CliBookkeepingEntryStructureParser.requiredPositiveUnitCost(rootNode),
        null,
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  static InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled
      readInventoryCapitalizationSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolInventoryPostingRequestFieldSets.inventoryCapitalizationSettledFields());
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  static InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit
      readInventoryCapitalizationOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolInventoryPostingRequestFieldSets.inventoryCapitalizationOnCreditFields());
    return new InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  static InventoryBookkeepingEntryVariants.InventoryWriteDown readInventoryWriteDownEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolInventoryPostingRequestFieldSets.inventoryWriteDownFields());
    return new InventoryBookkeepingEntryVariants.InventoryWriteDown(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.WRITE_DOWN_LOSS_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode));
  }

  static InventoryBookkeepingEntryVariants.InventoryShrinkage readInventoryShrinkageEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolInventoryPostingRequestFieldSets.inventoryShrinkageFields());
    return new InventoryBookkeepingEntryVariants.InventoryShrinkage(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.SHRINKAGE_LOSS_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveQuantity(rootNode),
        null);
  }

  static InventoryBookkeepingEntryVariants.InventoryCountIncrease readInventoryCountIncreaseEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolInventoryPostingRequestFieldSets.inventoryCountIncreaseFields());
    return new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.COUNT_GAIN_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveQuantity(rootNode),
        CliBookkeepingEntryStructureParser.requiredPositiveUnitCost(rootNode),
        null);
  }
}
