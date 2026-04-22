package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Structured fact describing the currency scope enforced inside one FinGrind book. */
public record BookCurrencyScopeFact(String value) {
  /** Validates one currency-scope fact. */
  public BookCurrencyScopeFact {
    value = ContractDescriptorValidation.requireText(value, "value");
  }
}
