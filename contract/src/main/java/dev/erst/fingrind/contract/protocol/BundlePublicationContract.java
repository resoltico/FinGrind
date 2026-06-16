package dev.erst.fingrind.contract.protocol;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Protocol-owned per-target publication facts shared by build and release surfaces. */
record BundlePublicationContract(
    Map<PublicCliBundleTarget, BundleLayoutContract.PublicBundlePublication> bundleTargets) {
  BundlePublicationContract {
    Objects.requireNonNull(bundleTargets, "bundleTargets");
    for (Map.Entry<PublicCliBundleTarget, BundleLayoutContract.PublicBundlePublication> entry :
        bundleTargets.entrySet()) {
      Objects.requireNonNull(entry.getKey(), "bundleTargets key");
      Objects.requireNonNull(entry.getValue(), "bundleTargets value");
    }
    bundleTargets = Collections.unmodifiableMap(new LinkedHashMap<>(bundleTargets));
  }

  void requireComplete(String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    Set<PublicCliBundleTarget> missingTargets = EnumSet.allOf(PublicCliBundleTarget.class);
    missingTargets.removeAll(bundleTargets.keySet());
    if (!missingTargets.isEmpty()) {
      throw new IllegalArgumentException(
          "bundleTargets must cover every public CLI bundle target in "
              + resourcePath
              + ": "
              + missingTargets);
    }
  }
}
