package dev.erst.fingrind.core;

import java.util.Objects;

/** Generated tax component that belongs to one typed accounting event. */
public record TaxComponent(
    TaxCode taxCode,
    TaxJurisdictionCode jurisdictionCode,
    TaxPricingMode pricingMode,
    TaxRecoverability recoverability,
    PercentageRate rate,
    PositiveMoney taxableAmount,
    PositiveMoney taxAmount) {
  /** Validates one generated tax component. */
  public TaxComponent {
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
    Objects.requireNonNull(pricingMode, "pricingMode");
    Objects.requireNonNull(recoverability, "recoverability");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(taxableAmount, "taxableAmount");
    Objects.requireNonNull(taxAmount, "taxAmount");
    if (!taxableAmount.currencyUnit().equals(taxAmount.currencyUnit())) {
      throw new IllegalArgumentException(
          "Tax component amounts must share one functional currency.");
    }
  }
}
