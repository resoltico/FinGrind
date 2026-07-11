package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/** Resolves the opening-only inventory pools that establish exact on-hand state. */
final class InventoryOpeningPositionResolver {
  private static final String OPENING_QUANTITY_FIELD = "openingBalances[].quantity";

  private InventoryOpeningPositionResolver() {}

  static InventoryPostingResolution resolve(
      BookkeepingEntry.OpeningPosition openingPosition, PostingValidationStore book) {
    List<OpeningInventoryResolution> resolutions =
        openingPosition.balances().stream()
            .filter(balance -> balance.quantity() != null)
            .map(balance -> resolveBalance(openingPosition.effectiveDate(), balance, book))
            .toList();
    return new InventoryPostingResolution(
        openingPosition,
        resolutions.stream().map(OpeningInventoryResolution::movement).toList(),
        resolutions.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    OpeningInventoryResolution::inventoryAccountCode,
                    OpeningInventoryResolution::resultingInventoryState)));
  }

  private static OpeningInventoryResolution resolveBalance(
      java.time.LocalDate effectiveDate,
      BookkeepingEntry.OpeningPosition.OpeningAccountBalance balance,
      PostingValidationStore book) {
    RegisteredAccount account =
        book.findAccount(balance.accountCode())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Opening inventory resolution requires declared account "
                            + balance.accountCode().value()
                            + "."));
    InventoryAccountContext inventoryContext =
        InventoryCostingStateSupport.inventoryContext(
            balance.accountCode(), effectiveDate, OPENING_QUANTITY_FIELD, book);
    if (inventoryContext.inventoryState().lastMovementDate().isPresent()) {
      throw new InventoryEntrySemanticsFailure(
          BookkeepingEntryModeSemanticsViolations.inventoryOpeningMustBeFirstMovement(
              balance.accountCode()),
          new IllegalStateException(
              "Inventory opening balance is not the first account movement."));
    }
    UnitOfMeasure unitOfMeasure = InventoryCostingStateSupport.requireUnitOfMeasure(account);
    var openingQuantityText = Objects.requireNonNull(balance.quantity(), "openingQuantity");
    Quantity openingQuantity =
        InventoryQuantityResolution.resolve(
            OPENING_QUANTITY_FIELD,
            openingQuantityText.value(),
            balance.accountCode(),
            unitOfMeasure,
            () -> openingQuantityText.resolve(unitOfMeasure));
    Money openingCost = balance.amount().toMoney();
    WeightedAverageCostingMath.InventoryPool openingPool =
        openingPool(openingQuantity, openingCost, balance.accountCode());
    return new OpeningInventoryResolution(
        balance.accountCode(),
        new InventoryMovementRecord(
            balance.accountCode(),
            effectiveDate,
            InventoryMovementKind.OPENING,
            openingQuantity.scaledUnits(),
            openingCost.minorUnits()),
        InventoryCostingStateSupport.resultingInventoryState(openingPool, effectiveDate));
  }

  private static WeightedAverageCostingMath.InventoryPool openingPool(
      Quantity openingQuantity, Money openingCost, AccountCode accountCode) {
    try {
      return new WeightedAverageCostingMath.InventoryPool(openingQuantity, openingCost);
    } catch (WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException
        | WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException exception) {
      throw new InventoryEntrySemanticsFailure(
          BookkeepingEntryModeSemanticsViolations.inventoryOpeningCarryingCostInvalid(accountCode),
          exception);
    }
  }

  private record OpeningInventoryResolution(
      AccountCode inventoryAccountCode,
      InventoryMovementRecord movement,
      InventoryAccountState resultingInventoryState) {}
}
