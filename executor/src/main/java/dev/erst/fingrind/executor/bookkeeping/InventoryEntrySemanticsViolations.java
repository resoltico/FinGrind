package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingInventoryRejectionSemantics;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.UnitOfMeasure;

/** Executor-local inventory event violations derived from canonical contract rejections. */
public final class InventoryEntrySemanticsViolations {
  private InventoryEntrySemanticsViolations() {}

  /** Creates the rejection for an inventory-only verb on a non-trading book template. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation verbRequiresTradingTemplate(
      String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    return selected(
        PostingInventoryRejectionSemantics.verbRequiresTradingTemplate(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            bookTemplateId),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for a trading sale that omits its required inventory relief. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      tradingSaleRequiresInventoryRelief(String selectorField, String selectorValue) {
    return selected(
        PostingInventoryRejectionSemantics.tradingSaleRequiresInventoryRelief(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue)),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for inventory relief supplied to a non-trading book. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryReliefRequiresTradingBook(
          String selectorField, String selectorValue, BookTemplateId bookTemplateId) {
    return selected(
        PostingInventoryRejectionSemantics.inventoryReliefRequiresTradingBook(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            bookTemplateId),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for quantity text that exceeds the inventory account's scale. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryQuantityIncompatibleWithUnitOfMeasure(
          String field,
          String quantityText,
          AccountCode inventoryAccountCode,
          UnitOfMeasure unitOfMeasure,
          String reason) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryQuantityIncompatibleWithUnitOfMeasure(
            field, quantityText, inventoryAccountCode, unitOfMeasure, reason));
  }

  /** Creates the rejection for a quantity and unit cost that cannot form an exact acquisition. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryAcquisitionCostNotExact(
          String quantityText,
          Money unitCost,
          AccountCode inventoryAccountCode,
          UnitOfMeasure unitOfMeasure) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryAcquisitionCostNotExact(
            quantityText, unitCost, inventoryAccountCode, unitOfMeasure));
  }

  /**
   * Creates the rejection for an acquisition whose resulting pool falls below the minor-unit floor.
   */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryAcquisitionBreachesMinorUnitFloor(
          String quantityText,
          Money unitCost,
          AccountCode inventoryAccountCode,
          UnitOfMeasure unitOfMeasure,
          long minimumRequiredMinorUnits,
          Money resultingCostPool) {
    return BookkeepingEntrySemanticsViolationSupport.toLocal(
        PostingInventoryRejectionSemantics.inventoryAcquisitionBreachesMinorUnitFloor(
            quantityText,
            unitCost,
            inventoryAccountCode,
            unitOfMeasure,
            minimumRequiredMinorUnits,
            resultingCostPool));
  }

  /**
   * Creates the rejection for FX facts whose functional amount differs from exact acquisition cost.
   */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
          String selectorField,
          String selectorValue,
          MonetaryAmount expectedFunctionalAmount,
          MonetaryAmount actualFunctionalAmount) {
    return selected(
        PostingInventoryRejectionSemantics
            .inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
                BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
                expectedFunctionalAmount,
                actualFunctionalAmount),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for a direct journal that contains an inventory-account line. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation rawJournalTouchesInventory(
      String selectorField, String selectorValue, AccountCode accountCode) {
    return selected(
        PostingInventoryRejectionSemantics.rawJournalTouchesInventory(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            accountCode),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for an opening inventory balance with no exact quantity. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      openingInventoryRequiresQuantity(
          String selectorField, String selectorValue, AccountCode accountCode) {
    return selected(
        PostingInventoryRejectionSemantics.openingInventoryRequiresQuantity(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            accountCode),
        selectorField,
        selectorValue);
  }

  /** Creates the rejection for quantity supplied to a non-inventory opening balance. */
  public static BookkeepingPostingRejection.EntrySemanticsViolation
      openingQuantityRequiresInventory(
          String selectorField, String selectorValue, AccountCode accountCode) {
    return selected(
        PostingInventoryRejectionSemantics.openingQuantityRequiresInventory(
            BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue),
            accountCode),
        selectorField,
        selectorValue);
  }

  private static BookkeepingPostingRejection.EntrySemanticsViolation selected(
      dev.erst.fingrind.contract.bookkeeping.PostingRejection.EntrySemanticsViolation violation,
      String selectorField,
      String selectorValue) {
    BookkeepingEntrySemanticsViolationSupport.requireCanonicalSelectorField(selectorField);
    BookkeepingEntrySemanticsViolationSupport.requireSelectorValue(selectorValue);
    return BookkeepingEntrySemanticsViolationSupport.toLocal(violation);
  }
}
