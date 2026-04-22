package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for one non-stdout export artifact supported by a command. */
public record ArtifactOutputDescriptor(String format, String option, String description) {
  /** Validates one artifact-output descriptor payload. */
  public ArtifactOutputDescriptor {
    format = ContractDescriptorValidation.requireText(format, "format");
    option = ContractDescriptorValidation.requireText(option, "option");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
