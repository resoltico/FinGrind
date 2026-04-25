package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the version payload. */
public record VersionDescriptor(String application, String version, String description)
    implements ContractDiscoveryDescriptor {
  /** Validates one version descriptor payload. */
  public VersionDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
