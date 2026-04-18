package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Protocol-owned metadata for public bundle targets and excluded operating systems. */
public record PublicDistributionContract(
    List<String> supportedPublicCliBundleTargets,
    List<String> unsupportedPublicCliOperatingSystems) {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/public-distribution-contract.properties";
  private static final String SUPPORTED_BUNDLE_TARGETS_KEY = "supportedPublicCliBundleTargets";
  private static final String UNSUPPORTED_OPERATING_SYSTEMS_KEY =
      "unsupportedPublicCliOperatingSystems";
  private static final PublicDistributionContract CURRENT = loadCurrent();

  /** Validates and copies one shared public-distribution contract snapshot. */
  public PublicDistributionContract {
    supportedPublicCliBundleTargets =
        normalize(supportedPublicCliBundleTargets, SUPPORTED_BUNDLE_TARGETS_KEY);
    unsupportedPublicCliOperatingSystems =
        normalize(unsupportedPublicCliOperatingSystems, UNSUPPORTED_OPERATING_SYSTEMS_KEY);
  }

  /** Returns the current protocol-owned public distribution contract. */
  public static PublicDistributionContract current() {
    return CURRENT;
  }

  private static PublicDistributionContract loadCurrent() {
    return loadFromResource(
        PublicDistributionContract.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }

  static PublicDistributionContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    Properties properties = new Properties();
    try (resourceStream) {
      if (resourceStream == null) {
        throw new IllegalStateException(
            "Missing public distribution contract resource: " + resourcePath);
      }
      properties.load(resourceStream);
    } catch (IOException exception) {
      throw new UncheckedIOException(
          "Failed to load public distribution contract resource: " + resourcePath, exception);
    }
    return new PublicDistributionContract(
        parseList(properties.getProperty(SUPPORTED_BUNDLE_TARGETS_KEY)),
        parseList(properties.getProperty(UNSUPPORTED_OPERATING_SYSTEMS_KEY)));
  }

  private static List<String> parseList(String rawValue) {
    if (rawValue == null || rawValue.isBlank()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (String value : rawValue.split(",", -1)) {
      values.add(value.trim());
    }
    return values;
  }

  private static List<String> normalize(List<String> values, String fieldName) {
    Objects.requireNonNull(values, fieldName);
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
