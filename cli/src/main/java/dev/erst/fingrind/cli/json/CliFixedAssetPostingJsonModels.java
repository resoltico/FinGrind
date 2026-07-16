package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Fixed-asset facts for caller-authored and executor-resolved posting payloads. */
public interface CliFixedAssetPostingJsonModels {
  /** Durable aggregate facts and executor-resolved components for one fixed-asset event. */
  record FixedAssetPayload(
      String fixedAssetId,
      String lifecycleKind,
      @Nullable String assetAccountCode,
      @Nullable String accumulatedDepreciationAccountCode,
      @Nullable String depreciationExpenseAccountCode,
      @Nullable String disposalGainAccountCode,
      @Nullable String disposalLossAccountCode,
      @Nullable MonetaryAmount cost,
      @Nullable FixedAssetDepreciationSchedulePayload depreciationSchedule,
      @Nullable ResolvedFixedAssetDepreciationPayload resolvedDepreciation,
      @Nullable ResolvedFixedAssetDisposalPayload resolvedDisposal) {
    public FixedAssetPayload {
      fixedAssetId = requireText(fixedAssetId, "fixedAssetId");
      lifecycleKind = requireText(lifecycleKind, "lifecycleKind");
      assetAccountCode = requireOptionalText(assetAccountCode, "assetAccountCode");
      accumulatedDepreciationAccountCode =
          requireOptionalText(
              accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      depreciationExpenseAccountCode =
          requireOptionalText(depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
      disposalGainAccountCode =
          requireOptionalText(disposalGainAccountCode, "disposalGainAccountCode");
      disposalLossAccountCode =
          requireOptionalText(disposalLossAccountCode, "disposalLossAccountCode");
    }
  }

  /** Straight-line terms retained when a fixed asset is capitalized. */
  record FixedAssetDepreciationSchedulePayload(
      String inServiceDate, int usefulLifeMonths, MonetaryAmount residualValue) {
    public FixedAssetDepreciationSchedulePayload {
      inServiceDate = requireText(inServiceDate, "inServiceDate");
      if (usefulLifeMonths < 1) {
        throw new IllegalArgumentException("usefulLifeMonths must be positive.");
      }
      Objects.requireNonNull(residualValue, "residualValue");
    }
  }

  /** Executor-derived depreciation journal facts for one fixed asset. */
  record ResolvedFixedAssetDepreciationPayload(
      String depreciationExpenseAccountCode,
      String accumulatedDepreciationAccountCode,
      MonetaryAmount amount) {
    public ResolvedFixedAssetDepreciationPayload {
      depreciationExpenseAccountCode =
          requireText(depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
      accumulatedDepreciationAccountCode =
          requireText(accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      Objects.requireNonNull(amount, "amount");
    }
  }

  /** Executor-derived carrying amount and gain-or-loss facts for one fixed-asset disposal. */
  record ResolvedFixedAssetDisposalPayload(
      String assetAccountCode,
      String accumulatedDepreciationAccountCode,
      String gainOrLossAccountCode,
      MonetaryAmount assetCost,
      MonetaryAmount accumulatedDepreciation,
      MonetaryAmount carryingAmount,
      MonetaryAmount gainOrLossAmount,
      boolean gain) {
    public ResolvedFixedAssetDisposalPayload {
      assetAccountCode = requireText(assetAccountCode, "assetAccountCode");
      accumulatedDepreciationAccountCode =
          requireText(accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      gainOrLossAccountCode = requireText(gainOrLossAccountCode, "gainOrLossAccountCode");
      Objects.requireNonNull(assetCost, "assetCost");
      Objects.requireNonNull(accumulatedDepreciation, "accumulatedDepreciation");
      Objects.requireNonNull(carryingAmount, "carryingAmount");
      Objects.requireNonNull(gainOrLossAmount, "gainOrLossAmount");
    }
  }
}
