package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** One general field descriptor for envelopes or emitted payloads. */
public record FieldDescriptor(String name, String description) implements ResponseDescriptorType {
  /** Validates one field descriptor payload. */
  public FieldDescriptor {
    name = ContractDescriptorValidation.requireText(name, "name");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
