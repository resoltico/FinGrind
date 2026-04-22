package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Stable verification states for the required SQLite compile-option contract. */
public enum SqliteCompileOptionsVerificationStatus {
  VERIFIED("verified"),
  NOT_VERIFIED("not-verified");

  private final String wireValue;

  SqliteCompileOptionsVerificationStatus(String wireValue) {
    this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
  }

  /** Returns the stable public wire value for this verification state. */
  public String wireValue() {
    return wireValue;
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
