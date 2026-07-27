package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.WireValue;

/** Stable relationship between preflight acceptance and the later commit attempt. */
public enum CommitGuarantee implements WireValue {
  NOT_GUARANTEED("not-guaranteed"),
  GUARANTEED("guaranteed");

  private final String wireValue;

  CommitGuarantee(String wireValue) {
    this.wireValue = ContractDescriptorValidation.requireText(wireValue, "wireValue");
  }

  /** Maps one boolean guarantee fact onto the stable enum contract. */
  public static CommitGuarantee fromGuaranteed(boolean guaranteed) {
    return guaranteed ? GUARANTEED : NOT_GUARANTEED;
  }

  /** Returns the stable public wire value for this guarantee status. */
  @Override
  public String wireValue() {
    return wireValue;
  }

  @Override
  public String toString() {
    return wireValue;
  }
}
