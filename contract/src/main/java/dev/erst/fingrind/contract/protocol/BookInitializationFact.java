package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing how one FinGrind book becomes initialized. */
public record BookInitializationFact(String value) {
  /** Validates one initialization fact. */
  public BookInitializationFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
