package dev.erst.fingrind.contract.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Properties;
import org.jspecify.annotations.Nullable;

/** Loads and publishes the current public-distribution contract snapshot. */
final class PublicDistributionContracts {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/public-distribution-contract.properties";
  private static final PublicDistributionContract CURRENT = loadCurrent();

  private PublicDistributionContracts() {}

  static PublicDistributionContract current() {
    return CURRENT;
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
        PublicDistributionContract.parseList(
            properties.getProperty(PublicDistributionContract.SUPPORTED_BUNDLE_TARGETS_KEY)),
        PublicDistributionContract.parseList(
            properties.getProperty(PublicDistributionContract.UNSUPPORTED_OPERATING_SYSTEMS_KEY)));
  }

  private static PublicDistributionContract loadCurrent() {
    return loadFromResource(
        PublicDistributionContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
