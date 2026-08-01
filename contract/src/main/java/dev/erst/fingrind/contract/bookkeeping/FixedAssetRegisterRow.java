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
    Optional<MonetaryAmount> carryingAmountAtDisposal,
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
    Objects.requireNonNull(carryingAmountAtDisposal, "carryingAmountAtDisposal");
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
    carryingAmountAtDisposal.ifPresent(
        amount -> {
          if (!cost.currencyCode().equals(amount.currencyCode())) {
            throw new IllegalArgumentException(
                "Fixed-asset disposal carrying amount must share the register currency.");
          }
        });
    if (disposedOn.isEmpty() && carryingAmountAtDisposal.isPresent()) {
      throw new IllegalArgumentException(
          "Active fixed-asset rows must not publish a disposal carrying amount.");
    }
    if (disposedOn.isPresent() && carryingAmountAtDisposal.isEmpty()) {
      throw new IllegalArgumentException(
          "Disposed fixed-asset rows must publish their carrying amount at disposal.");
    }
    if (disposedOn.isPresent() && carryingAmount.toMoney().minorUnits() != 0) {
      throw new IllegalArgumentException(
          "Disposed fixed-asset rows must have zero current carrying amount.");
    }
    MonetaryAmount reconciliationCarryingAmount = carryingAmountAtDisposal.orElse(carryingAmount);
    if (!cost.toMoney()
        .equals(accumulatedDepreciation.toMoney().plus(reconciliationCarryingAmount.toMoney()))) {
      throw new IllegalArgumentException(
          "Fixed-asset cost must equal accumulated depreciation plus the applicable carrying amount.");
    }
  }
}
