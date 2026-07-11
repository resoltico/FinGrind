package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Quantity;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory decrease that would drive exact quantity on hand below zero. */
public record InventoryQuantityBelowZero(
    AccountCode accountCode,
    String field,
    LocalDate effectiveDate,
    Quantity quantityOnHand,
    Quantity requestedDecreaseQuantity,
    Quantity resultingShortfallQuantity)
    implements PostingRejection.AccountStateViolation {
  public InventoryQuantityBelowZero {
    Objects.requireNonNull(accountCode, "accountCode");
    field = ContractDescriptorValidation.requireText(field, "field");
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
