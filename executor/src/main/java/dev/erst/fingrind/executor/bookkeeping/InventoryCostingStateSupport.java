package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/** Shared exact-state helpers for executor-owned inventory costing. */
final class InventoryCostingStateSupport {
  private InventoryCostingStateSupport() {}

  static WeightedAverageCostingMath.InventoryPool applyCompensatingMovement(
      WeightedAverageCostingMath.InventoryPool currentPool,
      InventoryMovementRecord movement,
      String field) {
    Quantity currentQuantity = currentPool.quantityOnHand();
    Money currentCostPool = currentPool.costPool();
    Quantity nextQuantity = adjustedQuantity(currentQuantity, movement, field);
    Money nextCostPool = adjustedCostPool(currentCostPool, movement, field);
    try {
      return new WeightedAverageCostingMath.InventoryPool(nextQuantity, nextCostPool);
    } catch (WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException
        | WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException exception) {
      long resultingShortfallMinorUnits =
          resultingCostShortfallMinorUnits(nextQuantity, nextCostPool, exception);
      throw new InventoryWriteDownExceedsCarryingCostFailure(
          movement.inventoryAccount(),
          field,
          movement.effectiveDate(),
          currentCostPool,
          Money.ofMinorUnits(currentCostPool.currencyUnit(), Math.abs(movement.costDeltaMinor())),
          Money.ofMinorUnits(currentCostPool.currencyUnit(), resultingShortfallMinorUnits),
          exception);
    }
  }

  private static Quantity adjustedQuantity(
      Quantity currentQuantity, InventoryMovementRecord movement, String field) {
    return switch (DeltaDirection.from(movement.quantityDelta())) {
      case INCREASE ->
          currentQuantity.plus(
              Quantity.ofScaledUnits(currentQuantity.scale(), movement.quantityDelta()));
      case DECREASE -> {
        Quantity removedQuantity =
            Quantity.ofScaledUnits(currentQuantity.scale(), Math.abs(movement.quantityDelta()));
        if (removedQuantity.compareTo(currentQuantity) > 0) {
          throw new InventoryQuantityBelowZeroFailure(
              movement.inventoryAccount(),
              field,
              movement.effectiveDate(),
              currentQuantity,
              removedQuantity,
              Quantity.ofScaledUnits(
                  currentQuantity.scale(),
                  Math.subtractExact(
                      removedQuantity.scaledUnits(), currentQuantity.scaledUnits())));
        }
        yield currentQuantity.minus(removedQuantity);
      }
      case NONE -> currentQuantity;
    };
  }

  private static Money adjustedCostPool(
      Money currentCostPool, InventoryMovementRecord movement, String field) {
    return switch (DeltaDirection.from(movement.costDeltaMinor())) {
      case INCREASE ->
          currentCostPool.plus(
              Money.ofMinorUnits(currentCostPool.currencyUnit(), movement.costDeltaMinor()));
      case DECREASE -> {
        Money removedCost =
            Money.ofMinorUnits(currentCostPool.currencyUnit(), Math.abs(movement.costDeltaMinor()));
        if (removedCost.compareTo(currentCostPool) > 0) {
          throw new InventoryWriteDownExceedsCarryingCostFailure(
              movement.inventoryAccount(),
              field,
              movement.effectiveDate(),
              currentCostPool,
              removedCost,
              Money.ofMinorUnits(
                  currentCostPool.currencyUnit(),
                  Math.subtractExact(removedCost.minorUnits(), currentCostPool.minorUnits())));
        }
        yield currentCostPool.minus(removedCost);
      }
      case NONE -> currentCostPool;
    };
  }

  static WeightedAverageCostingMath.Disposal disposeInventory(
      InventoryAccountContext inventoryContext, Quantity relievedQuantity, String field) {
    try {
      return WeightedAverageCostingMath.dispose(
          inventoryContext.inventoryState().pool(), relievedQuantity);
    } catch (WeightedAverageCostingMath.DisposedQuantityExceedsOnHandException exception) {
      Quantity quantityOnHand = exception.quantityOnHand();
      throw new InventoryQuantityBelowZeroFailure(
          inventoryContext.account().accountCode(),
          field,
          inventoryContext.effectiveDate(),
          quantityOnHand,
          exception.disposedQuantity(),
          Quantity.ofScaledUnits(
              quantityOnHand.scale(),
              Math.subtractExact(
                  exception.disposedQuantity().scaledUnits(), quantityOnHand.scaledUnits())),
          exception);
    }
  }

  static InventoryAccountContext inventoryContext(
      AccountCode inventoryAccountCode,
      LocalDate effectiveDate,
      String horizonField,
      PostingValidationStore book) {
    return inventoryContext(inventoryAccountCode, effectiveDate, horizonField, book, null);
  }

  static InventoryAccountContext inventoryContext(
      AccountCode inventoryAccountCode,
      LocalDate effectiveDate,
      String horizonField,
      PostingValidationStore book,
      @Nullable InventoryAccountState overridingState) {
    RegisteredAccount account =
        book.findAccount(inventoryAccountCode)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Inventory resolution requires declared account "
                            + inventoryAccountCode.value()
                            + "."));
    requireUnitOfMeasure(account);
    InventoryAccountState inventoryState =
        overridingState != null
            ? overridingState
            : book.findInventoryAccountState(inventoryAccountCode)
                .orElse(zeroInventoryState(account, book.requireInitializedBookIdentity()));
    requireHorizon(
        inventoryAccountCode,
        effectiveDate,
        Objects.requireNonNull(horizonField, "horizonField"),
        inventoryState.lastMovementDate());
    return new InventoryAccountContext(account, inventoryState, effectiveDate);
  }

  static InventoryAccountState zeroInventoryState(
      RegisteredAccount inventoryAccount, BookIdentity bookIdentity) {
    UnitOfMeasure unitOfMeasure = requireUnitOfMeasure(inventoryAccount);
    return new InventoryAccountState(
        WeightedAverageCostingMath.InventoryPool.zero(
            bookIdentity.functionalCurrency(), unitOfMeasure.quantityScale()),
        Optional.empty());
  }

  static UnitOfMeasure requireUnitOfMeasure(RegisteredAccount inventoryAccount) {
    UnitOfMeasure unitOfMeasure = inventoryAccount.unitOfMeasure();
    if (unitOfMeasure == null) {
      throw new IllegalStateException(
          "Inventory resolution requires one inventory account with one unit of measure.");
    }
    return unitOfMeasure;
  }

  static InventoryAccountState resultingInventoryState(
      WeightedAverageCostingMath.InventoryPool pool, LocalDate effectiveDate) {
    return new InventoryAccountState(pool, Optional.of(effectiveDate));
  }

  static void requireHorizon(
      AccountCode inventoryAccountCode,
      LocalDate effectiveDate,
      String field,
      Optional<LocalDate> lastMovementDate) {
    if (lastMovementDate.isPresent()) {
      LocalDate accountHorizonEffectiveDate = lastMovementDate.orElseThrow();
      if (!effectiveDate.isBefore(accountHorizonEffectiveDate)) {
        return;
      }
      throw new InventoryMovementPrecedesAccountHorizonFailure(
          inventoryAccountCode,
          Objects.requireNonNull(field, "field"),
          effectiveDate,
          accountHorizonEffectiveDate);
    }
  }

  private static long resultingCostShortfallMinorUnits(
      Quantity nextQuantity, Money nextCostPool, IllegalArgumentException exception) {
    if (exception
        instanceof WeightedAverageCostingMath.InventoryPoolMinorUnitFloorException floorException) {
      return Math.subtractExact(
          floorException.minimumRequiredMinorUnits(), floorException.costPool().minorUnits());
    }
    WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException zeroException =
        (WeightedAverageCostingMath.InventoryPoolZeroEquivalenceException) exception;
    return zeroException.quantityOnHand().isZero()
        ? zeroException.costPool().minorUnits()
        : Math.subtractExact(nextQuantity.scaledUnits(), nextCostPool.minorUnits());
  }

  /** Direction of one signed inventory movement component. */
  private enum DeltaDirection {
    INCREASE,
    DECREASE,
    NONE;

    private static DeltaDirection from(long delta) {
      if (delta == 0L) {
        return NONE;
      }
      return delta > 0L ? INCREASE : DECREASE;
    }
  }
}

record InventoryAccountContext(
    RegisteredAccount account, InventoryAccountState inventoryState, LocalDate effectiveDate) {
  InventoryAccountContext {
    Objects.requireNonNull(account, "account");
    Objects.requireNonNull(inventoryState, "inventoryState");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
  }
}
