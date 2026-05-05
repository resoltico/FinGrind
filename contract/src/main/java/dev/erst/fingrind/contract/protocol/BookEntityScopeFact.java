package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing the accounting-entity scope of one FinGrind book. */
public record BookEntityScopeFact(String value) {
  /** Validates one accounting-entity-scope fact. */
  public BookEntityScopeFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
