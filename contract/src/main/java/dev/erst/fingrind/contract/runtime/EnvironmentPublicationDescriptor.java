package dev.erst.fingrind.contract.runtime;

import dev.erst.fingrind.contract.discovery.ContractDiscoveryDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.PublicCliBundleTarget;
import dev.erst.fingrind.contract.protocol.PublicCliDistribution;
import java.util.List;

/** Descriptor for the public package surface that this runtime belongs to. */
public record EnvironmentPublicationDescriptor(
    PublicCliDistribution publicCliDistribution,
    List<PublicCliBundleTarget> supportedPublicCliBundleTargets,
    List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets,
    String sourceCheckoutJava)
    implements ContractDiscoveryDescriptor {
  /** Validates one publication descriptor payload. */
  public EnvironmentPublicationDescriptor {
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
