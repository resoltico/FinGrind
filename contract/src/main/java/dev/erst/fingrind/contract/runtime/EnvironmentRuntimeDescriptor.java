package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;

/** Descriptor for the launcher/runtime identity backing the active CLI process. */
public record EnvironmentRuntimeDescriptor(RuntimeDistribution runtimeDistribution)
    implements ContractDiscoveryDescriptor {
  /** Validates one runtime descriptor payload. */
  public EnvironmentRuntimeDescriptor {
    runtimeDistribution =
        ContractDescriptorValidation.requireValue(runtimeDistribution, "runtimeDistribution");
  }
}
