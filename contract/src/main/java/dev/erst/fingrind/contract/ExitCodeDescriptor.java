package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for one process exit code. */
public record ExitCodeDescriptor(int code, String meaning) {
  /** Validates one exit-code descriptor payload. */
  public ExitCodeDescriptor {
    if (code < 0) {
      throw new IllegalArgumentException("code must not be negative.");
    }
    meaning = ContractDescriptorValidation.requireText(meaning, "meaning");
  }
}
