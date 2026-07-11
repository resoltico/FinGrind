package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory cost decrease that would drive exact carrying cost below zero. */
public record InventoryWriteDownExceedsCarryingCostViolation(
    AccountCode accountCode,
    String field,
    LocalDate effectiveDate,
    Money carryingCostOnHand,
    Money requestedCostDecrease,
    Money resultingCostShortfall)
    implements BookkeepingPostingRejection.AccountStateViolation {
  public InventoryWriteDownExceedsCarryingCostViolation {
    Objects.requireNonNull(accountCode, "accountCode");
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank.");
    }
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
