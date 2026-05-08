package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Protocol-owned per-target bundle layout facts shared by build and operator surfaces. */
record BundleLayoutContract(Map<PublicCliBundleTarget, BundleTarget> bundleTargets) {
  BundleLayoutContract {
    Objects.requireNonNull(bundleTargets, "bundleTargets");
    for (Map.Entry<PublicCliBundleTarget, BundleTarget> entry : bundleTargets.entrySet()) {
      Objects.requireNonNull(entry.getKey(), "bundleTargets key");
      Objects.requireNonNull(entry.getValue(), "bundleTargets value");
    }
    Set<PublicCliBundleTarget> missingTargets = EnumSet.allOf(PublicCliBundleTarget.class);
    missingTargets.removeAll(bundleTargets.keySet());
    if (!missingTargets.isEmpty()) {
      throw new IllegalArgumentException(
          "bundleTargets must cover every public CLI bundle target: " + missingTargets);
    }
    bundleTargets = Map.copyOf(bundleTargets);
  }

  BundleTarget bundleTarget(PublicCliBundleTarget target) {
    return requireBundleTarget(bundleTargets, target);
  }

  /** One canonical self-contained bundle layout descriptor. */
  record BundleTarget(
      String operatingSystemId,
      String architectureId,
      String archiveFormat,
      String launcherPath,
      String launcherCommand,
      String sqliteLibraryFileName) {
    BundleTarget {
      operatingSystemId =
          ContractDescriptorValidation.requireText(operatingSystemId, "operatingSystemId");
      architectureId = ContractDescriptorValidation.requireText(architectureId, "architectureId");
      archiveFormat = ContractDescriptorValidation.requireText(archiveFormat, "archiveFormat");
      launcherPath = ContractDescriptorValidation.requireText(launcherPath, "launcherPath");
      launcherCommand =
          ContractDescriptorValidation.requireText(launcherCommand, "launcherCommand");
      sqliteLibraryFileName =
          ContractDescriptorValidation.requireText(sqliteLibraryFileName, "sqliteLibraryFileName");
    }
  }

  static BundleTarget requireBundleTarget(
      Map<PublicCliBundleTarget, BundleTarget> bundleTargets, PublicCliBundleTarget target) {
    Objects.requireNonNull(bundleTargets, "bundleTargets");
    PublicCliBundleTarget requiredTarget = Objects.requireNonNull(target, "target");
    BundleTarget bundleTarget = bundleTargets.get(requiredTarget);
    if (bundleTarget == null) {
      throw new IllegalStateException(
          "No bundle-layout contract is registered for bundle target " + requiredTarget + ".");
    }
    return bundleTarget;
  }
}
