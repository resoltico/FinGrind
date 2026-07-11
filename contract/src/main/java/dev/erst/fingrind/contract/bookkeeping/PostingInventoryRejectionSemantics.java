package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookTemplateId;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.UnitOfMeasure;

/**
 * Canonical public namespace for inventory-specific posting entry-semantics rejection factories.
 *
 * <p>Inventory write surfaces own exact quantity and carrying-cost truth, so the public rejection
 * vocabulary for inventory-only admission boundaries lives here rather than crowding the general
 * posting-semantics facade.
 */
public final class PostingInventoryRejectionSemantics {
  private PostingInventoryRejectionSemantics() {}

  /** Returns one refusal when an inventory-purchase verb targets a non-trading book. */
  public static PostingRejection.EntrySemanticsViolation verbRequiresTradingTemplate(
      String selectorValue, BookTemplateId bookTemplateId) {
    return PostingEntryInventoryRejectionSemantics.verbRequiresTradingTemplate(
        "entryKind", selectorValue, bookTemplateId);
  }

  /** Returns one refusal when a trading-template sale omits required inventory relief. */
  public static PostingRejection.EntrySemanticsViolation tradingSaleRequiresInventoryRelief(
      String selectorValue) {
    return PostingEntryInventoryRejectionSemantics.tradingSaleRequiresInventoryRelief(
        "entryKind", selectorValue);
  }

  /** Returns one refusal when inventory relief appears on a non-trading book. */
  public static PostingRejection.EntrySemanticsViolation inventoryReliefRequiresTradingBook(
      String selectorValue, BookTemplateId bookTemplateId) {
    return PostingEntryInventoryRejectionSemantics.inventoryReliefRequiresTradingBook(
        "entryKind", selectorValue, bookTemplateId);
  }

  /** Returns one refusal when inventory quantity text contradicts the account's unit scale. */
  public static PostingRejection.EntrySemanticsViolation
      inventoryQuantityIncompatibleWithUnitOfMeasure(
          String field,
          String quantityText,
          AccountCode inventoryAccountCode,
          UnitOfMeasure unitOfMeasure,
          String reason) {
    return PostingEntryInventoryRejectionSemantics.inventoryQuantityIncompatibleWithUnitOfMeasure(
        field, quantityText, inventoryAccountCode, unitOfMeasure, reason);
  }

  /**
   * Returns one refusal when one inventory acquisition cannot compose an exact minor-unit carrying
   * cost from quantity and unitCost.
   */
  public static PostingRejection.EntrySemanticsViolation inventoryAcquisitionCostNotExact(
      String quantityText,
      Money unitCost,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure) {
    return PostingEntryInventoryRejectionSemantics.inventoryAcquisitionCostNotExact(
        quantityText, unitCost, inventoryAccountCode, unitOfMeasure);
  }

  /**
   * Returns one refusal when one inventory acquisition would leave a positive pool below the
   * minimum minor-unit floor.
   */
  public static PostingRejection.EntrySemanticsViolation inventoryAcquisitionBreachesMinorUnitFloor(
      String quantityText,
      Money unitCost,
      AccountCode inventoryAccountCode,
      UnitOfMeasure unitOfMeasure,
      long minimumRequiredMinorUnits,
      Money resultingCostPool) {
    return PostingEntryInventoryRejectionSemantics.inventoryAcquisitionBreachesMinorUnitFloor(
        quantityText,
        unitCost,
        inventoryAccountCode,
        unitOfMeasure,
        minimumRequiredMinorUnits,
        resultingCostPool);
  }

  /**
   * Returns one refusal when FX functional amount conflicts with exact resolved acquisition cost.
   */
  public static PostingRejection.EntrySemanticsViolation
      inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
          String selectorValue,
          MonetaryAmount expectedFunctionalAmount,
          MonetaryAmount actualFunctionalAmount) {
    return PostingEntryInventoryRejectionSemantics
        .inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
            selectorValue, expectedFunctionalAmount, actualFunctionalAmount);
  }

  /** Returns one refusal when a raw direct journal contains an inventory-account line. */
  public static PostingRejection.EntrySemanticsViolation rawJournalTouchesInventory(
      String selectorValue, AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.rawJournalTouchesInventory(
        selectorValue, accountCode);
  }

  /** Returns one refusal when an inventory opening balance omits exact quantity. */
  public static PostingRejection.EntrySemanticsViolation openingInventoryRequiresQuantity(
      String selectorValue, AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.openingInventoryRequiresQuantity(
        selectorValue, accountCode);
  }

  /** Returns one refusal when a non-inventory opening balance carries quantity. */
  public static PostingRejection.EntrySemanticsViolation openingQuantityRequiresInventory(
      String selectorValue, AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.openingQuantityRequiresInventory(
        "entryKind", selectorValue, accountCode);
  }

  /** Returns one refusal when capitalization would create a cost-only inventory pool. */
  public static PostingRejection.EntrySemanticsViolation
      inventoryCapitalizationRequiresQuantityOnHand(AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.inventoryCapitalizationRequiresQuantityOnHand(
        accountCode);
  }

  /** Returns one refusal when an opening inventory balance is not its account's first movement. */
  public static PostingRejection.EntrySemanticsViolation inventoryOpeningMustBeFirstMovement(
      AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.inventoryOpeningMustBeFirstMovement(accountCode);
  }

  /** Returns one refusal when an inventory opening balance cannot establish an exact pool. */
  public static PostingRejection.EntrySemanticsViolation inventoryOpeningCarryingCostInvalid(
      AccountCode accountCode) {
    return PostingEntryInventoryRejectionSemantics.inventoryOpeningCarryingCostInvalid(accountCode);
  }
}
