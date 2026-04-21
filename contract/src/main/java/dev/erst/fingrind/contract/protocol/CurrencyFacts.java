package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Core-owned structured facts describing currency behavior in posting requests. */
public record CurrencyFacts(String scope, String multiCurrencyStatus, String description) {
  /** Validates one currency-facts payload. */
  public CurrencyFacts {
    scope = ContractDescriptorValidation.requireText(scope, "scope");
    multiCurrencyStatus =
        ContractDescriptorValidation.requireText(multiCurrencyStatus, "multiCurrencyStatus");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
