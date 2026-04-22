package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing the storage boundary of one FinGrind book. */
public record BookBoundaryFact(String value) {
  /** Validates one book-boundary fact. */
  public BookBoundaryFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
