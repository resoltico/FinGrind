package dev.erst.fingrind.contract.protocol;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Protocol-owned metadata for public bundle targets and excluded bundle targets. */
public record PublicDistributionContract(
    List<PublicCliBundleTarget> supportedPublicCliBundleTargets,
    List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets) {
  private static final ProtocolContractSchemaKeys.PublicDistribution SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().publicDistribution();
  static final String SUPPORTED_BUNDLE_TARGETS_KEY = SCHEMA_KEYS.supportedPublicCliBundleTargets();
  static final String UNSUPPORTED_BUNDLE_TARGETS_KEY =
      SCHEMA_KEYS.unsupportedPublicCliBundleTargets();

  /** Parses one wire-value snapshot into the typed public-distribution contract. */
  public static PublicDistributionContract fromWireValues(
      List<String> supportedPublicCliBundleTargets,
      List<String> unsupportedPublicCliBundleTargets) {
    return new PublicDistributionContract(
        normalize(supportedPublicCliBundleTargets, SUPPORTED_BUNDLE_TARGETS_KEY),
        normalize(unsupportedPublicCliBundleTargets, UNSUPPORTED_BUNDLE_TARGETS_KEY));
  }

  /** Validates one typed shared public-distribution contract snapshot. */
  public PublicDistributionContract {
    supportedPublicCliBundleTargets = List.copyOf(supportedPublicCliBundleTargets);
    unsupportedPublicCliBundleTargets = List.copyOf(unsupportedPublicCliBundleTargets);
    requireNoOverlap(supportedPublicCliBundleTargets, unsupportedPublicCliBundleTargets);
  }

  private static List<PublicCliBundleTarget> normalize(List<String> values, String fieldName) {
    Objects.requireNonNull(values, fieldName + " must not be null.");
    Set<PublicCliBundleTarget> unique = new LinkedHashSet<>();
    for (String value : values) {
      Objects.requireNonNull(value, fieldName);
      PublicCliBundleTarget target = PublicCliBundleTarget.fromWireValue(value.strip());
      if (!unique.add(target)) {
        throw new IllegalArgumentException(fieldName + " must not contain duplicates: " + target);
      }
    }
    return List.copyOf(unique);
  }

  private static void requireNoOverlap(
      List<PublicCliBundleTarget> supportedPublicCliBundleTargets,
      List<PublicCliBundleTarget> unsupportedPublicCliBundleTargets) {
    Set<PublicCliBundleTarget> overlap = new LinkedHashSet<>(supportedPublicCliBundleTargets);
    overlap.retainAll(unsupportedPublicCliBundleTargets);
    if (!overlap.isEmpty()) {
      throw new IllegalArgumentException(
          "supportedPublicCliBundleTargets and unsupportedPublicCliBundleTargets must be disjoint: "
              + overlap);
    }
  }
}
