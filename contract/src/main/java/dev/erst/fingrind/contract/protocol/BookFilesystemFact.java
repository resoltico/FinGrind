package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing how one FinGrind book maps onto the host filesystem. */
public record BookFilesystemFact(String value) {
  /** Validates one filesystem fact. */
  public BookFilesystemFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
