package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.protocol.PublicCliDistribution;
import dev.erst.fingrind.contract.protocol.RuntimeDistribution;
import java.util.List;

/** Descriptor for the public CLI distribution and runtime packaging contract. */
public record EnvironmentDistributionDescriptor(
    RuntimeDistribution runtimeDistribution,
    PublicCliDistribution publicCliDistribution,
    List<String> supportedPublicCliBundleTargets,
    List<String> unsupportedPublicCliOperatingSystems,
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
    unsupportedPublicCliOperatingSystems =
        ContractDescriptorValidation.copyList(
            unsupportedPublicCliOperatingSystems, "unsupportedPublicCliOperatingSystems");
    sourceCheckoutJava =
        ContractDescriptorValidation.requireText(sourceCheckoutJava, "sourceCheckoutJava");
  }
}
