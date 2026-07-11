package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.core.InventoryMovementKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Routes typed inventory events to their exact acquisition, disposal, or cost-adjustment resolver.
 */
final class InventoryCostingEngine {
  InventoryPostingResolution resolve(BookkeepingEntry entry, PostingValidationStore book) {
    Objects.requireNonNull(entry, "entry");
    Objects.requireNonNull(book, "book");
    return switch (entry) {
      case BookkeepingEntry.SaleSettled sale -> InventoryDisposalResolver.resolve(sale, book);
      case BookkeepingEntry.SaleOnCredit sale -> InventoryDisposalResolver.resolve(sale, book);
      case BookkeepingEntry.PurchaseSettled purchase ->
          InventoryAcquisitionResolver.resolve(purchase, book);
      case BookkeepingEntry.PurchaseOnCredit purchase ->
          InventoryAcquisitionResolver.resolve(purchase, book);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization ->
          InventoryAcquisitionResolver.resolve(capitalization, book);
      case InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization ->
          InventoryAcquisitionResolver.resolve(capitalization, book);
      case InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown ->
          resolveWriteDown(writeDown, book);
      case InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage ->
          InventoryDisposalResolver.resolve(shrinkage, book);
      case InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease ->
          InventoryAcquisitionResolver.resolve(countIncrease, book);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          InventoryOpeningPositionResolver.resolve(openingPosition, book);
      case BookkeepingEntry.Reversal reversal -> InventoryReversalResolver.resolve(reversal, book);
      default -> InventoryPostingResolution.withoutInventory(entry);
    };
  }

  private static InventoryPostingResolution resolveWriteDown(
      InventoryBookkeepingEntryVariants.InventoryWriteDown writeDown, PostingValidationStore book) {
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            writeDown.inventoryAccountCode(), writeDown.effectiveDate(), "amount", book);
    InventoryMovementRecord movement =
        new InventoryMovementRecord(
            writeDown.inventoryAccountCode(),
            writeDown.effectiveDate(),
            InventoryMovementKind.WRITE_DOWN,
            0L,
            Math.negateExact(writeDown.amount().toMoney().minorUnits()));
    var resultingPool =
        InventoryCostingStateSupport.applyCompensatingMovement(
            inventoryContext.inventoryState().pool(), movement, "amount");
    return new InventoryPostingResolution(
        writeDown,
        List.of(movement),
        Map.of(
            writeDown.inventoryAccountCode(),
            InventoryCostingStateSupport.resultingInventoryState(
                resultingPool, writeDown.effectiveDate())));
  }
}
