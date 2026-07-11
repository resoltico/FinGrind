package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.Quantity;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Exact inventory-pool valuation for one declared inventory account. */
public record InventoryValuationAccount(
    AccountCode inventoryAccountCode,
    AccountName inventoryAccountName,
    UnitOfMeasure unitOfMeasure,
    Quantity quantityOnHand,
    MonetaryAmount carryingValue,
    @Nullable MonetaryAmount roundedMovingAverageUnitCostProjection,
    List<InventoryValuationMovement> movements) {
  /** Validates one account valuation and keeps the rounded unit cost non-authoritative. */
  public InventoryValuationAccount {
    Objects.requireNonNull(inventoryAccountCode, "inventoryAccountCode");
    Objects.requireNonNull(inventoryAccountName, "inventoryAccountName");
    Objects.requireNonNull(unitOfMeasure, "unitOfMeasure");
    Objects.requireNonNull(quantityOnHand, "quantityOnHand");
    unitOfMeasure.requireCompatible(quantityOnHand);
    Objects.requireNonNull(carryingValue, "carryingValue");
    if (quantityOnHand.isZero() != carryingValue.toMoney().isZero()) {
      throw new IllegalArgumentException(
          "quantityOnHand and carryingValue must both be zero or both be positive.");
    }
    if (quantityOnHand.isZero() && roundedMovingAverageUnitCostProjection != null) {
      throw new IllegalArgumentException(
          "roundedMovingAverageUnitCostProjection is undefined at zero quantity.");
    }
    if (!quantityOnHand.isZero() && roundedMovingAverageUnitCostProjection == null) {
      throw new IllegalArgumentException(
          "roundedMovingAverageUnitCostProjection is required for positive quantity.");
    }
    if (roundedMovingAverageUnitCostProjection != null
        && !roundedMovingAverageUnitCostProjection
            .currencyCode()
            .equals(carryingValue.currencyCode())) {
      throw new IllegalArgumentException(
          "roundedMovingAverageUnitCostProjection must share carryingValue currency.");
    }
    movements = ContractDescriptorValidation.copyList(movements, "movements");
  }
}
