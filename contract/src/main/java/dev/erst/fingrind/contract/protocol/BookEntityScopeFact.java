package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing the real-world entity scope of one FinGrind book. */
public record BookEntityScopeFact(String value) {
  /** Validates one entity-scope fact. */
  public BookEntityScopeFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
