package dev.erst.fingrind.contract.bookkeeping;

import java.time.LocalDate;
import java.util.Objects;

/** Straight-line depreciation terms retained when a fixed asset is capitalized. */
public record FixedAssetDepreciationSchedule(
    LocalDate inServiceDate, int usefulLifeMonths, MonetaryAmount residualValue) {
  /** Validates schedule terms that make deterministic straight-line depreciation possible. */
  public FixedAssetDepreciationSchedule {
    Objects.requireNonNull(inServiceDate, "inServiceDate");
    if (usefulLifeMonths < 1 || usefulLifeMonths > 1_200) {
      throw new IllegalArgumentException("usefulLifeMonths must be between 1 and 1200.");
    }
    residualValue =
        BookkeepingEntryScalarValidationSupport.requireNonNegativeAmount(
            residualValue, "residualValue");
  }
}
