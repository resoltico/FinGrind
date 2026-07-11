package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.InventoryBookkeepingEntryVariants;
import dev.erst.fingrind.contract.bookkeeping.InventoryRelief;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryCosting;
import dev.erst.fingrind.contract.bookkeeping.ResolvedInventoryDisposal;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Resolves sales and shrinkage by disposing exact quantity and cost from the live inventory pool.
 */
final class InventoryDisposalResolver {
  private static final String SALE_INVENTORY_FIELD = "inventoryRelief.quantity";

  private InventoryDisposalResolver() {}

  static InventoryPostingResolution resolve(
      BookkeepingEntry.SaleSettled sale, PostingValidationStore book) {
    if (sale.inventoryRelief() == null) {
      return InventoryPostingResolution.withoutInventory(sale);
    }
    SaleResolution resolution =
        resolveSaleInventory(sale.effectiveDate(), sale.inventoryRelief(), book);
    return new InventoryPostingResolution(
        new BookkeepingEntry.SaleSettled(
            sale.effectiveDate(),
            sale.cashAccountCode(),
            sale.revenueAccountCode(),
            sale.amount(),
            sale.inventoryRelief(),
            resolution.resolvedInventoryCosting(),
            sale.foreignExchangeDetails(),
            sale.taxSelection(),
            sale.appliedTax()),
        List.of(resolution.movement()),
        Map.of(
            sale.inventoryRelief().inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      BookkeepingEntry.SaleOnCredit sale, PostingValidationStore book) {
    if (sale.inventoryRelief() == null) {
      return InventoryPostingResolution.withoutInventory(sale);
    }
    SaleResolution resolution =
        resolveSaleInventory(sale.effectiveDate(), sale.inventoryRelief(), book);
    return new InventoryPostingResolution(
        new BookkeepingEntry.SaleOnCredit(
            sale.effectiveDate(),
            sale.receivableAccountCode(),
            sale.revenueAccountCode(),
            sale.amount(),
            sale.inventoryRelief(),
            resolution.resolvedInventoryCosting(),
            sale.foreignExchangeDetails(),
            sale.taxSelection(),
            sale.appliedTax()),
        List.of(resolution.movement()),
        Map.of(
            sale.inventoryRelief().inventoryAccountCode(), resolution.resultingInventoryState()));
  }

  static InventoryPostingResolution resolve(
      InventoryBookkeepingEntryVariants.InventoryShrinkage shrinkage, PostingValidationStore book) {
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            shrinkage.inventoryAccountCode(), shrinkage.effectiveDate(), "quantity", book);
    UnitOfMeasure unitOfMeasure =
        InventoryCostingStateSupport.requireUnitOfMeasure(inventoryContext.account());
    Quantity disposedQuantity =
        InventoryQuantityResolution.resolve(
            "quantity",
            shrinkage.quantity().value(),
            shrinkage.inventoryAccountCode(),
            unitOfMeasure,
            () -> shrinkage.quantity().resolve(unitOfMeasure));
    WeightedAverageCostingMath.Disposal disposal =
        InventoryCostingStateSupport.disposeInventory(
            inventoryContext, disposedQuantity, "quantity");
    Money roundedProjection =
        WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(
            inventoryContext.inventoryState().pool());
    ResolvedInventoryDisposal resolvedInventoryDisposal =
        new ResolvedInventoryDisposal(disposal.costOfSales(), disposedQuantity, roundedProjection);
    InventoryMovementRecord movement =
        new InventoryMovementRecord(
            shrinkage.inventoryAccountCode(),
            shrinkage.effectiveDate(),
            InventoryMovementKind.SHRINKAGE,
            Math.negateExact(disposedQuantity.scaledUnits()),
            Math.negateExact(disposal.costOfSales().minorUnits()));
    return new InventoryPostingResolution(
        new InventoryBookkeepingEntryVariants.InventoryShrinkage(
            shrinkage.effectiveDate(),
            shrinkage.inventoryAccountCode(),
            shrinkage.shrinkageLossAccountCode(),
            shrinkage.quantity(),
            resolvedInventoryDisposal),
        List.of(movement),
        Map.of(
            shrinkage.inventoryAccountCode(),
            InventoryCostingStateSupport.resultingInventoryState(
                disposal.remainingPool(), shrinkage.effectiveDate())));
  }

  private static SaleResolution resolveSaleInventory(
      LocalDate effectiveDate, InventoryRelief inventoryRelief, PostingValidationStore book) {
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            inventoryRelief.inventoryAccountCode(), effectiveDate, SALE_INVENTORY_FIELD, book);
    UnitOfMeasure unitOfMeasure =
        InventoryCostingStateSupport.requireUnitOfMeasure(inventoryContext.account());
    Quantity relievedQuantity =
        InventoryQuantityResolution.resolve(
            SALE_INVENTORY_FIELD,
            inventoryRelief.quantity().value(),
            inventoryContext.account().accountCode(),
            unitOfMeasure,
            () -> inventoryRelief.quantity().resolve(unitOfMeasure));
    WeightedAverageCostingMath.Disposal disposal =
        InventoryCostingStateSupport.disposeInventory(
            inventoryContext, relievedQuantity, SALE_INVENTORY_FIELD);
    Money roundedProjection =
        WeightedAverageCostingMath.roundedMovingAverageUnitCostProjection(
            inventoryContext.inventoryState().pool());
    ResolvedInventoryCosting resolvedInventoryCosting =
        new ResolvedInventoryCosting(disposal.costOfSales(), relievedQuantity, roundedProjection);
    InventoryMovementRecord movement =
        new InventoryMovementRecord(
            inventoryContext.account().accountCode(),
            effectiveDate,
            InventoryMovementKind.DISPOSAL,
            Math.negateExact(relievedQuantity.scaledUnits()),
            Math.negateExact(disposal.costOfSales().minorUnits()));
    return new SaleResolution(
        resolvedInventoryCosting,
        movement,
        InventoryCostingStateSupport.resultingInventoryState(
            disposal.remainingPool(), effectiveDate));
  }

  private record SaleResolution(
      ResolvedInventoryCosting resolvedInventoryCosting,
      InventoryMovementRecord movement,
      InventoryAccountState resultingInventoryState) {}
}
