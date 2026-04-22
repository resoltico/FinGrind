package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing how one book governs declared-account registration. */
public record BookAccountRegistryFact(String value) {
  /** Validates one account-registry fact. */
  public BookAccountRegistryFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
