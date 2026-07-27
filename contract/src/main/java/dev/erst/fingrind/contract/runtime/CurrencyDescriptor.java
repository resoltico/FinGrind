package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for currency support. */
public record CurrencyDescriptor(String scope, String multiCurrencyStatus, String description)
    implements ResponseDescriptorType {
  /** Validates one currency descriptor payload. */
  public CurrencyDescriptor {
    scope = ContractDescriptorValidation.requireText(scope, "scope");
    multiCurrencyStatus =
        ContractDescriptorValidation.requireText(multiCurrencyStatus, "multiCurrencyStatus");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
