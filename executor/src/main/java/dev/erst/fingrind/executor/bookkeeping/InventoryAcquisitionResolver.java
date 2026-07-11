package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryAcquisition;
import dev.erst.fingrind.contract.fx.ForeignExchangeDetails;
import dev.erst.fingrind.contract.tax.AppliedTax;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.util.List;
import java.util.Map;

/**
 * Resolves quantity-increasing inventory events and cost-only capitalization from exact pool state.
 */
final class InventoryAcquisitionResolver {
  private static final String PURCHASE_QUANTITY_FIELD = "quantity";

  private InventoryAcquisitionResolver() {}

  static InventoryPostingResolution resolve(
      BookkeepingEntry.PurchaseSettled purchase, PostingValidationStore book) {
    PurchaseResolution resolution =
        resolvePurchase(
            purchase.effectiveDate(),
            purchase.inventoryAccountCode(),
            purchase.quantity().value(),
            purchase.unitCost().toMoney(),
            purchase.appliedTax(),
            purchase.entryKind().wireValue(),
            purchase.foreignExchangeDetails(),
            book);
    return new InventoryPostingResolution(
        new BookkeepingEntry.PurchaseSettled(
            purchase.effectiveDate(),
            purchase.inventoryAccountCode(),
            purchase.cashAccountCode(),
            purchase.quantity(),
            purchase.unitCost(),
            resolution.resolvedInventoryAcquisition(),
            purchase.foreignExchangeDetails(),
            purchase.taxSelection(),
            purchase.appliedTax()),
        List.of(resolution.movement()),
        Map.of(purchase.inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      BookkeepingEntry.PurchaseOnCredit purchase, PostingValidationStore book) {
    PurchaseResolution resolution =
        resolvePurchase(
            purchase.effectiveDate(),
            purchase.inventoryAccountCode(),
            purchase.quantity().value(),
            purchase.unitCost().toMoney(),
            purchase.appliedTax(),
            purchase.entryKind().wireValue(),
            purchase.foreignExchangeDetails(),
            book);
    return new InventoryPostingResolution(
        new BookkeepingEntry.PurchaseOnCredit(
            purchase.effectiveDate(),
            purchase.inventoryAccountCode(),
            purchase.payableAccountCode(),
            purchase.quantity(),
            purchase.unitCost(),
            resolution.resolvedInventoryAcquisition(),
            purchase.foreignExchangeDetails(),
            purchase.taxSelection(),
            purchase.appliedTax()),
        List.of(resolution.movement()),
        Map.of(purchase.inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      InventoryBookkeepingEntryVariants.InventoryCapitalizationSettled capitalization,
      PostingValidationStore book) {
    CapitalizationResolution resolution =
        resolveCapitalization(
            capitalization.effectiveDate(),
            capitalization.inventoryAccountCode(),
            capitalization.amount().toMoney(),
            capitalization.appliedTax(),
            book);
    return new InventoryPostingResolution(
        capitalization,
        List.of(resolution.movement()),
        Map.of(capitalization.inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      InventoryBookkeepingEntryVariants.InventoryCapitalizationOnCredit capitalization,
      PostingValidationStore book) {
    CapitalizationResolution resolution =
        resolveCapitalization(
            capitalization.effectiveDate(),
            capitalization.inventoryAccountCode(),
            capitalization.amount().toMoney(),
            capitalization.appliedTax(),
            book);
    return new InventoryPostingResolution(
        capitalization,
        List.of(resolution.movement()),
        Map.of(capitalization.inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      InventoryBookkeepingEntryVariants.InventoryCountIncrease countIncrease,
      PostingValidationStore book) {
    PurchaseResolution acquisition =
        resolvePurchase(
            countIncrease.effectiveDate(),
            countIncrease.inventoryAccountCode(),
            countIncrease.quantity().value(),
            countIncrease.unitCost().toMoney(),
            null,
            countIncrease.entryKind().wireValue(),
            null,
            book);
    InventoryMovementRecord movement =
        new InventoryMovementRecord(
            countIncrease.inventoryAccountCode(),
            countIncrease.effectiveDate(),
            InventoryMovementKind.COUNT_INCREASE,
            acquisition.movement().quantityDelta(),
            acquisition.movement().costDeltaMinor());
    return new InventoryPostingResolution(
        new InventoryBookkeepingEntryVariants.InventoryCountIncrease(
            countIncrease.effectiveDate(),
            countIncrease.inventoryAccountCode(),
            countIncrease.countGainAccountCode(),
            countIncrease.quantity(),
            countIncrease.unitCost(),
            acquisition.resolvedInventoryAcquisition()),
        List.of(movement),
        Map.of(countIncrease.inventoryAccountCode(), acquisition.resultingInventoryState()));
  }

  private static PurchaseResolution resolvePurchase(
      java.time.LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      String quantityText,
      Money unitCost,
      @org.jspecify.annotations.Nullable AppliedTax appliedTax,
      String entryKind,
      @org.jspecify.annotations.Nullable ForeignExchangeDetails foreignExchangeDetails,
      PostingValidationStore book) {
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            inventoryAccountCode, effectiveDate, PURCHASE_QUANTITY_FIELD, book);
    UnitOfMeasure unitOfMeasure =
        InventoryCostingStateSupport.requireUnitOfMeasure(inventoryContext.account());
    Quantity acquiredQuantity =
        InventoryQuantityResolution.resolve(
            PURCHASE_QUANTITY_FIELD,
            quantityText,
            inventoryAccountCode,
            unitOfMeasure,
            () -> unitOfMeasure.parseQuantity(quantityText));
    WeightedAverageCostingMath.InventoryPool updatedPool;
    try {
      updatedPool =
          WeightedAverageCostingMath.acquire(
              inventoryContext.inventoryState().pool(), acquiredQuantity, unitCost);
    } catch (WeightedAverageCostingMath.InexactAcquisitionCostException exception) {
      throw new InventoryEntrySemanticsFailure(
          InventoryEntrySemanticsViolations.inventoryAcquisitionCostNotExact(
              quantityText, unitCost, inventoryAccountCode, unitOfMeasure),
          exception);
    } catch (WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException exception) {
      throw new InventoryEntrySemanticsFailure(
          InventoryEntrySemanticsViolations.inventoryAcquisitionBreachesMinorUnitFloor(
              quantityText,
              unitCost,
              inventoryAccountCode,
              unitOfMeasure,
              exception.minimumRequiredMinorUnits(),
              exception.costPool()),
          exception);
    }
    Money preTaxCost =
        updatedPool.costPool().minus(inventoryContext.inventoryState().pool().costPool());
    requireForeignExchangeFunctionalAmount(
        entryKind, foreignExchangeDetails, MonetaryAmount.of(preTaxCost));
    Money carryingCost = carryingCost(preTaxCost, appliedTax);
    WeightedAverageCostingMath.InventoryPool carryingCostPool =
        carryingCost.equals(preTaxCost)
            ? updatedPool
            : new WeightedAverageCostingMath.InventoryPool(
                updatedPool.quantityOnHand(),
                updatedPool.costPool().plus(carryingCost.minus(preTaxCost)));
    return new PurchaseResolution(
        new ResolvedInventoryAcquisition(
            acquiredQuantity, MonetaryAmount.of(preTaxCost), MonetaryAmount.of(carryingCost)),
        new InventoryMovementRecord(
            inventoryAccountCode,
            effectiveDate,
            InventoryMovementKind.ACQUISITION,
            acquiredQuantity.scaledUnits(),
            carryingCost.minorUnits()),
        InventoryCostingStateSupport.resultingInventoryState(carryingCostPool, effectiveDate));
  }

  private static void requireForeignExchangeFunctionalAmount(
      String entryKind,
      @org.jspecify.annotations.Nullable ForeignExchangeDetails foreignExchangeDetails,
      MonetaryAmount preTaxCost) {
    if (foreignExchangeDetails == null
        || preTaxCost.equals(foreignExchangeDetails.functionalAmount())) {
      return;
    }
    throw new InventoryEntrySemanticsFailure(
        InventoryEntrySemanticsViolations
            .inventoryAcquisitionForeignExchangeFunctionalAmountMismatch(
                "entryKind", entryKind, preTaxCost, foreignExchangeDetails.functionalAmount()),
        new IllegalArgumentException(
            "Foreign-exchange functional amount does not match acquisition cost."));
  }

  private static CapitalizationResolution resolveCapitalization(
      java.time.LocalDate effectiveDate,
      AccountCode inventoryAccountCode,
      Money preTaxCost,
      @org.jspecify.annotations.Nullable AppliedTax appliedTax,
      PostingValidationStore book) {
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            inventoryAccountCode, effectiveDate, "amount", book);
    if (inventoryContext.inventoryState().pool().quantityOnHand().isZero()) {
      throw new InventoryEntrySemanticsFailure(
          BookkeepingEntryModeSemanticsViolations.inventoryCapitalizationRequiresQuantityOnHand(
              inventoryAccountCode),
          new IllegalStateException(
              "Cost-only capitalization cannot establish a zero-quantity pool."));
    }
    Money carryingCost = carryingCost(preTaxCost, appliedTax);
    InventoryMovementRecord movement =
        new InventoryMovementRecord(
            inventoryAccountCode,
            effectiveDate,
            InventoryMovementKind.CAPITALIZATION,
            0L,
            carryingCost.minorUnits());
    var resultingPool =
        InventoryCostingStateSupport.applyCompensatingMovement(
            inventoryContext.inventoryState().pool(), movement, "amount");
    return new CapitalizationResolution(
        movement,
        InventoryCostingStateSupport.resultingInventoryState(resultingPool, effectiveDate));
  }

  private static Money carryingCost(
      Money preTaxCost, @org.jspecify.annotations.Nullable AppliedTax appliedTax) {
    if (appliedTax == null
        || appliedTax.applicationKind() == TaxApplicationKind.INPUT_EXPENSE_RECOVERABLE) {
      return preTaxCost;
    }
    return appliedTax.grossAmount().toMoney();
  }

  private record PurchaseResolution(
      ResolvedInventoryAcquisition resolvedInventoryAcquisition,
      InventoryMovementRecord movement,
      InventoryAccountState resultingInventoryState) {}

  private record CapitalizationResolution(
      InventoryMovementRecord movement, InventoryAccountState resultingInventoryState) {}
}
