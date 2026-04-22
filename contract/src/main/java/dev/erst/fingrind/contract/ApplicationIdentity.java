package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Stable identity fields that appear on discovery descriptors. */
public record ApplicationIdentity(String application, String version, String description) {
  /** Validates one stable application identity descriptor. */
  public ApplicationIdentity {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
