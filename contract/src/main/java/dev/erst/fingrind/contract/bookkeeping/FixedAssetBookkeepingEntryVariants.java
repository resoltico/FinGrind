package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** Typed write variants owned by the fixed-assets context. */
public sealed interface FixedAssetBookkeepingEntryVariants extends TypedBookkeepingEntry
    permits FixedAssetBookkeepingEntryVariants.Capitalization,
        FixedAssetBookkeepingEntryVariants.Depreciation,
        FixedAssetBookkeepingEntryVariants.Disposal {
  /** Capitalizes one identifiable asset and fixes its straight-line depreciation schedule. */
  record Capitalization(
      LocalDate effectiveDate,
      FixedAssetId fixedAssetId,
      AccountCode assetAccountCode,
      AccountCode accumulatedDepreciationAccountCode,
      AccountCode depreciationExpenseAccountCode,
      AccountCode disposalGainAccountCode,
      AccountCode disposalLossAccountCode,
      AccountCode cashAccountCode,
      MonetaryAmount cost,
      FixedAssetDepreciationSchedule depreciationSchedule)
      implements FixedAssetBookkeepingEntryVariants {
    public Capitalization {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(fixedAssetId, "fixedAssetId");
      assetAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              assetAccountCode, "assetAccountCode");
      accumulatedDepreciationAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
      depreciationExpenseAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
      disposalGainAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              disposalGainAccountCode, "disposalGainAccountCode");
      disposalLossAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              disposalLossAccountCode, "disposalLossAccountCode");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      cost = BookkeepingEntryScalarValidationSupport.requirePositiveAmount(cost, "cost");
      java.util.Objects.requireNonNull(depreciationSchedule, "depreciationSchedule");
      if (!cost.currencyCode().equals(depreciationSchedule.residualValue().currencyCode())) {
        throw new IllegalArgumentException("cost and residualValue must use one currency.");
      }
      if (depreciationSchedule.residualValue().toMoney().minorUnits()
          >= cost.toMoney().minorUnits()) {
        throw new IllegalArgumentException("residualValue must be lower than cost.");
      }
      if (depreciationSchedule.inServiceDate().isBefore(effectiveDate)) {
        throw new IllegalArgumentException(
            "depreciationSchedule.inServiceDate must not precede effectiveDate.");
      }
    }
  }

  /** Records one executor-calculated depreciation charge for one admitted asset. */
  record Depreciation(
      LocalDate effectiveDate,
      FixedAssetId fixedAssetId,
      @Nullable ResolvedFixedAssetDepreciation resolvedDepreciation)
      implements FixedAssetBookkeepingEntryVariants {
    public Depreciation {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(fixedAssetId, "fixedAssetId");
    }
  }

  /** Disposes one admitted asset for cash and lets the executor derive its gain or loss. */
  record Disposal(
      LocalDate effectiveDate,
      FixedAssetId fixedAssetId,
      AccountCode cashAccountCode,
      MonetaryAmount proceeds,
      @Nullable ResolvedFixedAssetDisposal resolvedDisposal)
      implements FixedAssetBookkeepingEntryVariants {
    public Disposal {
      effectiveDate = BookkeepingEntryScalarValidationSupport.requireEffectiveDate(effectiveDate);
      java.util.Objects.requireNonNull(fixedAssetId, "fixedAssetId");
      cashAccountCode =
          BookkeepingEntryScalarValidationSupport.requireAccountCode(
              cashAccountCode, "cashAccountCode");
      proceeds =
          BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(proceeds, "proceeds");
    }
  }
}
