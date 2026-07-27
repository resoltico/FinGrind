package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.WireValue;

/** Stable initialization requirements for account-registry operations. */
public enum InitializationRequirement implements WireValue {
  REQUIRES_OPEN_BOOK("requires-open-book");

  private final String wireValue;

  InitializationRequirement(String wireValue) {
    this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this requirement. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
