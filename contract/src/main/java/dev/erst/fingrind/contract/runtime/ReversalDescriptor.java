package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the reversal model. */
public record ReversalDescriptor(String model, List<String> requirements)
    implements ResponseDescriptorType {
  /** Validates one reversal descriptor payload. */
  public ReversalDescriptor {
    model = ContractDescriptorValidation.requireText(model, "model");
    requirements = ContractDescriptorValidation.copyList(requirements, "requirements");
  }
}
