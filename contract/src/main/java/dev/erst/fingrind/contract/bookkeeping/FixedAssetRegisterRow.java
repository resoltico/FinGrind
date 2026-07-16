package dev.erst.fingrind.contract.bookkeeping;

import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Exact durable lifecycle state for one fixed asset. */
public record FixedAssetRegisterRow(
    FixedAssetId fixedAssetId,
    LocalDate capitalizedOn,
    AccountCode assetAccountCode,
    AccountCode accumulatedDepreciationAccountCode,
    MonetaryAmount cost,
    MonetaryAmount accumulatedDepreciation,
    MonetaryAmount carryingAmount,
    FixedAssetDepreciationSchedule depreciationSchedule,
    int depreciationPeriodsApplied,
    Optional<LocalDate> latestLifecycleEffectiveDate,
    Optional<LocalDate> disposedOn) {
  /** Validates exact fixed-asset lifecycle facts. */
  public FixedAssetRegisterRow {
    Objects.requireNonNull(fixedAssetId, "fixedAssetId");
    Objects.requireNonNull(capitalizedOn, "capitalizedOn");
    Objects.requireNonNull(assetAccountCode, "assetAccountCode");
    Objects.requireNonNull(
        accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
    Objects.requireNonNull(cost, "cost");
    Objects.requireNonNull(accumulatedDepreciation, "accumulatedDepreciation");
    Objects.requireNonNull(carryingAmount, "carryingAmount");
    Objects.requireNonNull(depreciationSchedule, "depreciationSchedule");
    Objects.requireNonNull(latestLifecycleEffectiveDate, "latestLifecycleEffectiveDate");
    Objects.requireNonNull(disposedOn, "disposedOn");
    if (depreciationPeriodsApplied < 0) {
      throw new IllegalArgumentException("depreciationPeriodsApplied must not be negative.");
    }
    if (!cost.currencyCode().equals(accumulatedDepreciation.currencyCode())
        || !cost.currencyCode().equals(carryingAmount.currencyCode())) {
      throw new IllegalArgumentException("Fixed-asset register amounts must share one currency.");
    }
    if (!cost.toMoney().equals(accumulatedDepreciation.toMoney().plus(carryingAmount.toMoney()))) {
      throw new IllegalArgumentException(
          "Fixed-asset cost must equal accumulated depreciation plus carrying amount.");
    }
  }
}
