package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Loads and publishes the current public-distribution contract snapshot. */
final class PublicDistributionContracts {
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/public-distribution-contract.json";
  private static final PublicDistributionContract CURRENT = loadCurrent();

  private PublicDistributionContracts() {}

  static PublicDistributionContract current() {
    return CURRENT;
  }

  static PublicDistributionContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "public distribution contract");
    return PublicDistributionContract.fromWireValues(
        JsonContractResourceSupport.optionalStringArray(
            document, PublicDistributionContract.SUPPORTED_BUNDLE_TARGETS_KEY),
        JsonContractResourceSupport.optionalStringArray(
            document, PublicDistributionContract.UNSUPPORTED_BUNDLE_TARGETS_KEY));
  }

  private static PublicDistributionContract loadCurrent() {
    return loadFromResource(
        PublicDistributionContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
