package dev.erst.fingrind.contract.protocol;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Protocol-owned metadata for public bundle targets and excluded operating systems. */
public record PublicDistributionContract(
    List<String> supportedPublicCliBundleTargets,
    List<String> unsupportedPublicCliOperatingSystems) {
  static final String SUPPORTED_BUNDLE_TARGETS_KEY = "supportedPublicCliBundleTargets";
  static final String UNSUPPORTED_OPERATING_SYSTEMS_KEY = "unsupportedPublicCliOperatingSystems";

  /** Validates and copies one shared public-distribution contract snapshot. */
  public PublicDistributionContract(
      @Nullable List<String> supportedPublicCliBundleTargets,
      @Nullable List<String> unsupportedPublicCliOperatingSystems) {
    this.supportedPublicCliBundleTargets =
        normalize(supportedPublicCliBundleTargets, SUPPORTED_BUNDLE_TARGETS_KEY);
    this.unsupportedPublicCliOperatingSystems =
        normalize(unsupportedPublicCliOperatingSystems, UNSUPPORTED_OPERATING_SYSTEMS_KEY);
  }

  static List<String> parseList(@Nullable String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (String value : rawValue.split(",", -1)) {
      values.add(value.trim());
    }
    return values;
  }

  private static List<String> normalize(@Nullable List<String> values, String fieldName) {
    if (values == null) {
      return List.of();
    }
    Set<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      Objects.requireNonNull(value, fieldName);
      if (value.isBlank()) {
        throw new IllegalArgumentException(fieldName + " must not contain blank values.");
      }
      if (!unique.add(value)) {
        throw new IllegalArgumentException(fieldName + " must not contain duplicates: " + value);
      }
    }
    return List.copyOf(unique);
  }
}
