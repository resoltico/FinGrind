package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory decrease that would drive exact quantity on hand below zero. */
public record InventoryQuantityBelowZeroViolation(
    AccountCode accountCode,
    String field,
    LocalDate effectiveDate,
    Quantity quantityOnHand,
    Quantity requestedDecreaseQuantity,
    Quantity resultingShortfallQuantity)
    implements BookkeepingPostingRejection.AccountStateViolation {
  public InventoryQuantityBelowZeroViolation {
    Objects.requireNonNull(accountCode, "accountCode");
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank.");
    }
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(quantityOnHand, "quantityOnHand");
    Objects.requireNonNull(requestedDecreaseQuantity, "requestedDecreaseQuantity");
    Objects.requireNonNull(resultingShortfallQuantity, "resultingShortfallQuantity");
    if (!requestedDecreaseQuantity.isPositive()) {
      throw new IllegalArgumentException("requestedDecreaseQuantity must be positive.");
    }
    if (!resultingShortfallQuantity.isPositive()) {
      throw new IllegalArgumentException("resultingShortfallQuantity must be positive.");
    }
  }
}
