package dev.erst.fingrind.core;

import java.util.Objects;
import java.util.Optional;

/** Canonical tax-code definition with neutral pricing and settlement behavior. */
public record TaxCodeDefinition(
    TaxCode taxCode,
    TaxCodeName displayName,
    TaxJurisdictionCode jurisdictionCode,
    PercentageRate rate,
    TaxPricingMode pricingMode,
    TaxRecoverability recoverability,
    AccountCode liabilityAccountCode,
    Optional<AccountCode> receivableAccountCode) {
  /** Validates one tax-code definition. */
  public TaxCodeDefinition {
    Objects.requireNonNull(taxCode, "taxCode");
    Objects.requireNonNull(displayName, "displayName");
    Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
    Objects.requireNonNull(rate, "rate");
    Objects.requireNonNull(pricingMode, "pricingMode");
    Objects.requireNonNull(recoverability, "recoverability");
    Objects.requireNonNull(liabilityAccountCode, "liabilityAccountCode");
    Objects.requireNonNull(receivableAccountCode, "receivableAccountCode");
  }
}
