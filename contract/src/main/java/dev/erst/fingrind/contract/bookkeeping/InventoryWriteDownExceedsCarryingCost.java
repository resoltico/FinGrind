package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory cost decrease that would drive exact carrying cost below zero. */
public record InventoryWriteDownExceedsCarryingCost(
    AccountCode accountCode,
    String field,
    LocalDate effectiveDate,
    Money carryingCostOnHand,
    Money requestedCostDecrease,
    Money resultingCostShortfall)
    implements PostingRejection.AccountStateViolation {
  public InventoryWriteDownExceedsCarryingCost {
    Objects.requireNonNull(accountCode, "accountCode");
    field = ContractDescriptorValidation.requireText(field, "field");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(carryingCostOnHand, "carryingCostOnHand");
    Objects.requireNonNull(requestedCostDecrease, "requestedCostDecrease");
    Objects.requireNonNull(resultingCostShortfall, "resultingCostShortfall");
    if (!requestedCostDecrease.isPositive()) {
      throw new IllegalArgumentException("requestedCostDecrease must be positive.");
    }
    if (!resultingCostShortfall.isPositive()) {
      throw new IllegalArgumentException("resultingCostShortfall must be positive.");
    }
  }
}
