package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory movement whose effective date precedes the account's durable movement horizon. */
public record InventoryMovementPrecedesAccountHorizonViolation(
    AccountCode accountCode,
    String field,
    LocalDate attemptedEffectiveDate,
    LocalDate accountHorizonEffectiveDate)
    implements BookkeepingPostingRejection.AccountStateViolation {
  public InventoryMovementPrecedesAccountHorizonViolation {
    Objects.requireNonNull(accountCode, "accountCode");
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank.");
    }
    Objects.requireNonNull(attemptedEffectiveDate, "attemptedEffectiveDate");
    Objects.requireNonNull(accountHorizonEffectiveDate, "accountHorizonEffectiveDate");
  }
}
