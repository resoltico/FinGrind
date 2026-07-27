package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.WireValue;

/** Exhaustive transport categories for published non-success responses. */
public enum FailureCategory implements WireValue {
  STRUCTURAL_INVALID("structural-invalid"),
  DOMAIN_SEMANTIC("domain-semantic"),
  PRECONDITION("precondition"),
  UNSUPPORTED_SELECTION("unsupported-selection"),
  INTERNAL("internal");

  private final String wireValue;

  FailureCategory(String wireValue) {
    this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
  }

  /** Returns the stable machine token for this failure category. */
  @Override
  public String wireValue() {
    return wireValue;
  }
}
