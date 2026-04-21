package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Core-owned structured facts describing preflight semantics. */
public record PreflightFacts(String semantics, boolean commitGuarantee, String description) {
  /** Validates one preflight-facts payload. */
  public PreflightFacts {
    semantics = ContractDescriptorValidation.requireText(semantics, "semantics");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
