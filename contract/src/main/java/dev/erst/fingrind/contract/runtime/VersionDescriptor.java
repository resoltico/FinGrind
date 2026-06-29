package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;

/** Descriptor for the version payload. */
public record VersionDescriptor(
    String application, String version, String protocolVersion, String description)
    implements ContractDiscoveryDescriptor {
  /** Validates one version descriptor payload. */
  public VersionDescriptor {
    application = ContractDescriptorValidation.requireText(application, "application");
    version = ContractDescriptorValidation.requireText(version, "version");
    protocolVersion = ContractDescriptorValidation.requireText(protocolVersion, "protocolVersion");
    description = ContractDescriptorValidation.requireText(description, "description");
  }
}
