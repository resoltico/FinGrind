package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.InventoryMovementKind;
import dev.erst.fingrind.core.WeightedAverageCostingMath;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Applies compensating inventory movements for one typed posting reversal. */
final class InventoryReversalResolver {
  private static final String REVERSAL_MOVEMENT_FIELD = "reversal.priorPostingId";

  private InventoryReversalResolver() {}

  static InventoryPostingResolution resolve(
      BookkeepingEntry.Reversal reversal, PostingValidationStore book) {
    List<InventoryMovementRecord> priorMovements =
        book.inventoryMovements(reversal.reversal().reference().priorPostingId());
    if (priorMovements.isEmpty()) {
      return InventoryPostingResolution.withoutInventory(reversal);
    }
    List<InventoryMovementRecord> compensatingMovements = new ArrayList<>();
    Map<AccountCode, InventoryAccountState> resultingStates = new ConcurrentHashMap<>();
    List<InventoryMovementRecord> replayOrder = new ArrayList<>(priorMovements);
    java.util.Collections.reverse(replayOrder);
    for (InventoryMovementRecord priorMovement : replayOrder) {
      InventoryAccountContext inventoryContext =
          InventoryCostingStateSupport.inventoryContext(
              priorMovement.inventoryAccount(),
              reversal.effectiveDate(),
              REVERSAL_MOVEMENT_FIELD,
              book,
              Optional.ofNullable(resultingStates.get(priorMovement.inventoryAccount()))
                  .orElse(null));
      InventoryMovementRecord compensatingMovement =
          compensatingMovement(priorMovement, reversal.effectiveDate());
      WeightedAverageCostingMath.InventoryPool resultingPool =
          InventoryCostingStateSupport.applyCompensatingMovement(
              inventoryContext.inventoryState().pool(),
              compensatingMovement,
              REVERSAL_MOVEMENT_FIELD);
      compensatingMovements.add(compensatingMovement);
      resultingStates.put(
          priorMovement.inventoryAccount(),
          InventoryCostingStateSupport.resultingInventoryState(
              resultingPool, reversal.effectiveDate()));
    }
    return new InventoryPostingResolution(
        reversal, List.copyOf(compensatingMovements), Map.copyOf(resultingStates));
  }

  private static InventoryMovementRecord compensatingMovement(
      InventoryMovementRecord priorMovement, LocalDate effectiveDate) {
    Objects.requireNonNull(priorMovement, "priorMovement");
    return switch (priorMovement.kind()) {
      case ACQUISITION,
          CAPITALIZATION,
          COUNT_INCREASE,
          OPENING,
          DISPOSAL,
          WRITE_DOWN,
          SHRINKAGE,
          REVERSAL_COMP ->
          new InventoryMovementRecord(
              priorMovement.inventoryAccount(),
              effectiveDate,
              InventoryMovementKind.REVERSAL_COMP,
              Math.negateExact(priorMovement.quantityDelta()),
              Math.negateExact(priorMovement.costDeltaMinor()));
    };
  }
}
