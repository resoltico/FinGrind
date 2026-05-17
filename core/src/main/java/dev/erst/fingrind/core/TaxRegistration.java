package dev.erst.fingrind.core;

import java.util.Objects;

/** Canonical tax-registration fact for one accounting entity. */
public record TaxRegistration(
    TaxJurisdictionCode jurisdictionCode,
    TaxRegistrationId registrationId,
    TaxFilingFrequency filingFrequency) {
  /** Validates one tax-registration fact. */
  public TaxRegistration {
    Objects.requireNonNull(jurisdictionCode, "jurisdictionCode");
    Objects.requireNonNull(registrationId, "registrationId");
    Objects.requireNonNull(filingFrequency, "filingFrequency");
  }
}
