package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.PublicCliDistribution;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.List;

/** Descriptor for the public CLI distribution and runtime packaging contract. */
public record EnvironmentDistributionDescriptor(
    RuntimeDistribution runtimeDistribution,
    PublicCliDistribution publicCliDistribution,
    List<PublicCliBundleTarget> supportedPublicCliBundleTargets,
    List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets,
    String sourceCheckoutJava)
    implements ContractDiscoveryDescriptor {
  /** Validates one distribution descriptor payload. */
  public EnvironmentDistributionDescriptor {
    runtimeDistribution =
        ContractDescriptorValidation.requireValue(runtimeDistribution, "runtimeDistribution");
    publicCliDistribution =
        ContractDescriptorValidation.requireValue(publicCliDistribution, "publicCliDistribution");
    supportedPublicCliBundleTargets =
        ContractDescriptorValidation.copyList(
            supportedPublicCliBundleTargets, "supportedPublicCliBundleTargets");
    unsupportedPublicCliBundleTargets =
        ContractDescriptorValidation.copyList(
            unsupportedPublicCliBundleTargets, "unsupportedPublicCliBundleTargets");
    sourceCheckoutJava =
        ContractDescriptorValidation.requireText(sourceCheckoutJava, "sourceCheckoutJava");
  }
}
