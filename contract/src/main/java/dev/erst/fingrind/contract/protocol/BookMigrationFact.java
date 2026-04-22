package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing the migration policy for one FinGrind book. */
public record BookMigrationFact(String value) {
  /** Validates one migration fact. */
  public BookMigrationFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
