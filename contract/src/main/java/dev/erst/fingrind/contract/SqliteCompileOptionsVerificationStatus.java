package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.WireValue;

/** Stable verification states for the required SQLite compile-option contract. */
public enum SqliteCompileOptionsVerificationStatus implements WireValue {
  VERIFIED("verified"),
  NOT_VERIFIED("not-verified");

  private final String wireValue;

  SqliteCompileOptionsVerificationStatus(String wireValue) {
    this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this verification state. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
