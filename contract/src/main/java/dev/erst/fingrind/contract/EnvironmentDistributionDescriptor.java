package dev.erst.fingrind.contract;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.List;

/** Descriptor for the public CLI distribution and runtime packaging contract. */
public record EnvironmentDistributionDescriptor(
    String runtimeDistribution,
    String publicCliDistribution,
    List<String> supportedPublicCliBundleTargets,
    List<String> unsupportedPublicCliOperatingSystems,
    String sourceCheckoutJava) {
  /** Validates one distribution descriptor payload. */
  public EnvironmentDistributionDescriptor {
    runtimeDistribution =
        ContractDescriptorValidation.requireText(runtimeDistribution, "runtimeDistribution");
    publicCliDistribution =
        ContractDescriptorValidation.requireText(publicCliDistribution, "publicCliDistribution");
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
