package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory movement whose effective date precedes the account movement horizon. */
public record InventoryMovementPrecedesAccountHorizon(
    AccountCode accountCode,
    String field,
    LocalDate attemptedEffectiveDate,
    LocalDate accountHorizonEffectiveDate)
    implements PostingRejection.AccountStateViolation {
  public InventoryMovementPrecedesAccountHorizon {
    Objects.requireNonNull(accountCode, "accountCode");
    field = ContractDescriptorValidation.requireText(field, "field");
    Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    Objects.requireNonNull(accountHorizonEffectiveDate, "accountHorizonEffectiveDate");
  }
}
