package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Executor-owned settlement carrying value and realized gain-or-loss facts. */
public record ResolvedRealizedForeignExchangeSettlement(
    AccountCode receivableAccountCode,
    AccountCode gainOrLossAccountCode,
    MonetaryAmount carryingAmount,
    MonetaryAmount realizedGainOrLossAmount,
    boolean gain) {
  /** Validates one reconciled realized foreign-exchange settlement result. */
  public ResolvedRealizedForeignExchangeSettlement {
    Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
    Objects.requireNonNull(gainOrLossAccountCode, "gainOrLossAccountCode");
    carryingAmount =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(
            carryingAmount, "carryingAmount");
    realizedGainOrLossAmount =
        BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(
            realizedGainOrLossAmount, "realizedGainOrLossAmount");
    if (!carryingAmount.currencyCode().equals(realizedGainOrLossAmount.currencyCode())) {
      throw new IllegalArgumentException(
          "Realized foreign-exchange settlement amounts must use one currency.");
    }
  }
}
