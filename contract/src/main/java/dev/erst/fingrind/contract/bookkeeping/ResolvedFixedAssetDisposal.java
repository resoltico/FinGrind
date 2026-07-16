package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.util.Objects;

/** Executor-owned carrying-value and gain-or-loss facts for one fixed-asset disposal. */
public record ResolvedFixedAssetDisposal(
    AccountCode assetAccountCode,
    AccountCode accumulatedDepreciationAccountCode,
    AccountCode gainOrLossAccountCode,
    MonetaryAmount assetCost,
    MonetaryAmount accumulatedDepreciation,
    MonetaryAmount carryingAmount,
    MonetaryAmount gainOrLossAmount,
    boolean gain) {
  /** Validates a disposal result whose carrying amount and gain or loss reconcile exactly. */
  public ResolvedFixedAssetDisposal {
    Objects.requireNonNull(assetAccountCode, "assetAccountCode");
    Objects.requireNonNull(
        accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
    Objects.requireNonNull(gainOrLossAccountCode, "gainOrLossAccountCode");
    assetCost =
        BookkeepingEntryScalarValidationSupport.requirePositiveAmount(assetCost, "assetCost");
    accumulatedDepreciation =
        BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(
            accumulatedDepreciation, "accumulatedDepreciation");
    carryingAmount =
        BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(
            carryingAmount, "carryingAmount");
    gainOrLossAmount =
        BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(
            gainOrLossAmount, "gainOrLossAmount");
    if (!assetCost.currencyCode().equals(accumulatedDepreciation.currencyCode())
        || !assetCost.currencyCode().equals(carryingAmount.currencyCode())
        || !assetCost.currencyCode().equals(gainOrLossAmount.currencyCode())) {
      throw new IllegalArgumentException("Fixed-asset disposal amounts must use one currency.");
    }
    long expectedCarrying =
        assetCost.toMoney().minorUnits() - accumulatedDepreciation.toMoney().minorUnits();
    if (expectedCarrying < 0) {
      throw new IllegalArgumentException(
          "Fixed-asset disposal accumulatedDepreciation must not exceed assetCost.");
    }
    if (expectedCarrying != carryingAmount.toMoney().minorUnits()) {
      throw new IllegalArgumentException(
          "Fixed-asset disposal carryingAmount must equal assetCost minus accumulatedDepreciation.");
    }
  }
}
