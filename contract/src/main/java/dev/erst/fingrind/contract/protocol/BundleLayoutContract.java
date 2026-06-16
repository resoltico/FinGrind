package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    bundleTargets = Collections.unmodifiableMap(new LinkedHashMap<>(bundleTargets));
  }

  BundleTarget bundleTarget(PublicCliBundleTarget target) {
    return requireBundleTarget(bundleTargets, target);
  }

  List<PublicCliBundleTarget> supportedPublicCliBundleTargets() {
    List<PublicCliBundleTarget> supportedTargets = new ArrayList<>();
    for (PublicCliBundleTarget target : PublicCliBundleTarget.values()) {
      if (bundleTarget(target).publicBundlePublication().status()
          == PublicBundlePublicationStatus.PUBLISHED) {
        supportedTargets.add(target);
      }
    }
    return List.copyOf(supportedTargets);
  }

  List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets() {
    List<PublicCliBundleTarget> unsupportedTargets = new ArrayList<>();
    for (PublicCliBundleTarget target : PublicCliBundleTarget.values()) {
      if (bundleTarget(target).publicBundlePublication().status()
          != PublicBundlePublicationStatus.PUBLISHED) {
        unsupportedTargets.add(target);
      }
    }
    return List.copyOf(unsupportedTargets);
  }

  /** One canonical self-contained bundle layout descriptor. */
  record BundleTarget(
      String operatingSystemId,
      String architectureId,
      String archiveFormat,
      String launcherPath,
      String launcherCommand,
      String sqliteLibraryFileName,
      String compatibilityLabel,
      Optional<String> minimumGlibcVersion,
      Optional<String> compatibilitySmokeContainerImage,
      PublicBundlePublication publicBundlePublication) {
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
      compatibilityLabel =
          ContractDescriptorValidation.requireText(compatibilityLabel, "compatibilityLabel");
      minimumGlibcVersion =
          Optional.ofNullable(
              ContractDescriptorValidation.requireOptionalText(
                  Objects.requireNonNull(minimumGlibcVersion, "minimumGlibcVersion").orElse(null),
                  "minimumGlibcVersion"));
      compatibilitySmokeContainerImage =
          Optional.ofNullable(
              ContractDescriptorValidation.requireOptionalText(
                  Objects.requireNonNull(
                          compatibilitySmokeContainerImage, "compatibilitySmokeContainerImage")
                      .orElse(null),
                  "compatibilitySmokeContainerImage"));
      PublicBundlePublication normalizedPublicBundlePublication =
          Objects.requireNonNull(publicBundlePublication, "publicBundlePublication");
      publicBundlePublication = normalizedPublicBundlePublication;
      if ("linux".equals(operatingSystemId) && minimumGlibcVersion.isEmpty()) {
        throw new IllegalArgumentException(
            "minimumGlibcVersion must be present for linux bundle targets.");
      }
      if ("linux".equals(operatingSystemId) && compatibilitySmokeContainerImage.isEmpty()) {
        throw new IllegalArgumentException(
            "compatibilitySmokeContainerImage must be present for linux bundle targets.");
      }
      if (!"linux".equals(operatingSystemId) && minimumGlibcVersion.isPresent()) {
        throw new IllegalArgumentException(
            "minimumGlibcVersion must be absent for non-linux bundle targets.");
      }
      if (!"linux".equals(operatingSystemId) && compatibilitySmokeContainerImage.isPresent()) {
        throw new IllegalArgumentException(
            "compatibilitySmokeContainerImage must be absent for non-linux bundle targets.");
      }
    }
  }

  /** Per-target public-publication facts rooted in the canonical bundle-target registry. */
  record PublicBundlePublication(
      PublicBundlePublicationStatus status,
      Optional<String> runnerLabel,
      Optional<String> expectedRunnerOs,
      Optional<String> expectedRunnerArch) {
    PublicBundlePublication {
      PublicBundlePublicationStatus normalizedStatus = Objects.requireNonNull(status, "status");
      status = normalizedStatus;
      runnerLabel =
          Optional.ofNullable(
              ContractDescriptorValidation.requireOptionalText(
                  Objects.requireNonNull(runnerLabel, "runnerLabel").orElse(null), "runnerLabel"));
      expectedRunnerOs =
          Optional.ofNullable(
              ContractDescriptorValidation.requireOptionalText(
                  Objects.requireNonNull(expectedRunnerOs, "expectedRunnerOs").orElse(null),
                  "expectedRunnerOs"));
      expectedRunnerArch =
          Optional.ofNullable(
              ContractDescriptorValidation.requireOptionalText(
                  Objects.requireNonNull(expectedRunnerArch, "expectedRunnerArch").orElse(null),
                  "expectedRunnerArch"));
      if (status == PublicBundlePublicationStatus.PUBLISHED) {
        if (runnerLabel.isEmpty() || expectedRunnerOs.isEmpty() || expectedRunnerArch.isEmpty()) {
          throw new IllegalArgumentException(
              "published bundle targets must declare runnerLabel, expectedRunnerOs, and expectedRunnerArch.");
        }
      } else if (runnerLabel.isPresent()
          || expectedRunnerOs.isPresent()
          || expectedRunnerArch.isPresent()) {
        throw new IllegalArgumentException(
            "non-published bundle targets must omit runnerLabel, expectedRunnerOs, and expectedRunnerArch.");
      }
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
