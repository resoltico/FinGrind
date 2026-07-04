package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BalanceSide;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;

/** One inventory-account decrease that would create or deepen one credit inventory balance. */
public record InventoryBalanceBelowZeroViolation(
    AccountCode accountCode,
    String field,
    LocalDate effectiveDate,
    BalanceSide currentBalanceSide,
    Money currentNetAmount,
    Money requestedDecreaseAmount,
    Money resultingCreditBalance)
    implements BookkeepingPostingRejection.AccountStateViolation {
  public InventoryBalanceBelowZeroViolation {
    Objects.requireNonNull(accountCode, "accountCode");
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must not be blank.");
    }
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    Objects.requireNonNull(currentBalanceSide, "currentBalanceSide");
    Objects.requireNonNull(currentNetAmount, "currentNetAmount");
    Objects.requireNonNull(requestedDecreaseAmount, "requestedDecreaseAmount");
    Objects.requireNonNull(resultingCreditBalance, "resultingCreditBalance");
    if (!requestedDecreaseAmount.isPositive()) {
      throw new IllegalArgumentException("requestedDecreaseAmount must be positive.");
    }
    if (!resultingCreditBalance.isPositive()) {
      throw new IllegalArgumentException("resultingCreditBalance must be positive.");
    }
    if (currentBalanceSide == BalanceSide.ZERO && !currentNetAmount.isZero()) {
      throw new IllegalArgumentException(
          "currentNetAmount must be zero when currentBalanceSide is ZERO.");
    }
    if (currentBalanceSide != BalanceSide.ZERO && !currentNetAmount.isPositive()) {
      throw new IllegalArgumentException(
          "currentNetAmount must be positive when currentBalanceSide is not ZERO.");
    }
  }
}
