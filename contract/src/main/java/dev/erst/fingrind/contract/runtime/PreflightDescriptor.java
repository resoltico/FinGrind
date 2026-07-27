package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for preflight semantics. */
public record PreflightDescriptor(
    String semantics, CommitGuarantee commitGuarantee, String description)
    implements ResponseDescriptorType {
  /** Validates one preflight descriptor payload. */
  public PreflightDescriptor {
    semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    commitGuarantee = ContractDescriptorValidation.requireValue(commitGuarantee, "commitGuarantee");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
