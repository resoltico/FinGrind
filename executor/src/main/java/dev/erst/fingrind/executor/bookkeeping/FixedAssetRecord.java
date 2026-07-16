package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.FixedAssetDepreciationSchedule;
import dev.erst.fingrind.contract.bookkeeping.FixedAssetId;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.Money;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable fixed-asset aggregate with its consumed straight-line depreciation and disposal state.
 */
public record FixedAssetRecord(
    FixedAssetId fixedAssetId,
    LocalDate capitalizedOn,
    AccountCode assetAccountCode,
    AccountCode accumulatedDepreciationAccountCode,
    AccountCode depreciationExpenseAccountCode,
    AccountCode disposalGainAccountCode,
    AccountCode disposalLossAccountCode,
    Money cost,
    FixedAssetDepreciationSchedule depreciationSchedule,
    Money accumulatedDepreciation,
    int depreciationPeriodsApplied,
    Optional<LocalDate> latestLifecycleEffectiveDate,
    Optional<LocalDate> disposedOn) {
  /** Validates retained fixed-asset lifecycle facts. */
  public FixedAssetRecord {
    Objects.requireNonNull(fixedAssetId, "fixedAssetId");
    Objects.requireNonNull(capitalizedOn, "capitalizedOn");
    Objects.requireNonNull(assetAccountCode, "assetAccountCode");
    Objects.requireNonNull(
        accumulatedDepreciationAccountCode, "accumulatedDepreciationAccountCode");
    Objects.requireNonNull(depreciationExpenseAccountCode, "depreciationExpenseAccountCode");
    Objects.requireNonNull(disposalGainAccountCode, "disposalGainAccountCode");
    Objects.requireNonNull(disposalLossAccountCode, "disposalLossAccountCode");
    Objects.requireNonNull(cost, "cost");
    Objects.requireNonNull(depreciationSchedule, "depreciationSchedule");
    Objects.requireNonNull(accumulatedDepreciation, "accumulatedDepreciation");
    Objects.requireNonNull(latestLifecycleEffectiveDate, "latestLifecycleEffectiveDate");
    Objects.requireNonNull(disposedOn, "disposedOn");
    if (!cost.isPositive()) {
      throw new IllegalArgumentException("Fixed-asset cost must be positive.");
    }
    if (!cost.currencyUnit().equals(accumulatedDepreciation.currencyUnit())
        || !cost.currencyUnit()
            .code()
            .equals(depreciationSchedule.residualValue().currencyCode())) {
      throw new IllegalArgumentException("Fixed-asset money facts must use one currency.");
    }
    if (accumulatedDepreciation.compareTo(cost) > 0) {
      throw new IllegalArgumentException(
          "Accumulated depreciation must not exceed fixed-asset cost.");
    }
    if (depreciationPeriodsApplied < 0
        || depreciationPeriodsApplied > depreciationSchedule.usefulLifeMonths()) {
      throw new IllegalArgumentException(
          "Fixed-asset depreciation period count is outside its schedule.");
    }
    latestLifecycleEffectiveDate.ifPresent(
        date -> {
          if (date.isBefore(capitalizedOn)) {
            throw new IllegalArgumentException(
                "Fixed-asset lifecycle horizon must not precede capitalization.");
          }
        });
    disposedOn.ifPresent(
        date -> {
          if (date.isBefore(capitalizedOn)) {
            throw new IllegalArgumentException(
                "Fixed-asset disposal must not precede capitalization.");
          }
        });
  }

  /** Returns the undepreciated carrying value at the stored lifecycle horizon. */
  public Money carryingAmount() {
    return cost.minus(accumulatedDepreciation);
  }

  /** Returns the exact unconsumed depreciable cost. */
  public Money remainingDepreciableAmount() {
    return carryingAmount().minus(depreciationSchedule.residualValue().toMoney());
  }

  /** Returns whether the asset may receive another depreciation charge. */
  public boolean depreciable() {
    return disposedOn.isEmpty()
        && depreciationPeriodsApplied < depreciationSchedule.usefulLifeMonths()
        && remainingDepreciableAmount().isPositive();
  }

  /** Returns the inclusive effective-date floor for the next lifecycle event. */
  public LocalDate lifecycleHorizon() {
    return latestLifecycleEffectiveDate.orElse(capitalizedOn);
  }
}
