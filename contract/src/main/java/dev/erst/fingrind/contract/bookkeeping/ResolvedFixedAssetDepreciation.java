package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Executor-owned fixed-asset depreciation facts resolved from the retained asset lifecycle. */
public record ResolvedFixedAssetDepreciation(
    AccountCode depreciationExpenseAccountCode,
    AccountCode accumulatedDepreciationAccountCode,
    MonetaryAmount amount) {
  /** Validates one resolved depreciation journal pair. */
  public ResolvedFixedAssetDepreciation {
    Objects.requireNonNull(depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
    Objects.requireNonNull(
        accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
    amount = BookkeepingEntryScalarValidationSupport.requirePositiveAmount(amount, "amount");
  }
}
