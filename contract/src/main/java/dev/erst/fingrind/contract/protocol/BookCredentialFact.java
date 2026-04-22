package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing how one FinGrind book receives credentials. */
public record BookCredentialFact(String value) {
  /** Validates one credential fact. */
  public BookCredentialFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
