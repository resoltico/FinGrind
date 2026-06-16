package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Loads and publishes the current bundle-publication contract snapshot. */
final class BundlePublicationContracts {
  private static final ProtocolContractSchemaKeys.BundlePublication SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().bundlePublication();
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/bundle-publication-contract.json";
  private static final String BUNDLE_TARGETS_KEY = SCHEMA_KEYS.bundleTargets();
  private static final BundlePublicationContract CURRENT = loadCurrent();

  private BundlePublicationContracts() {}

  static BundlePublicationContract current() {
    return CURRENT;
  }

  static BundlePublicationContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    ObjectNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "bundle publication contract");
    ObjectNode bundleTargetsNode =
        JsonContractResourceSupport.requireObject(
            document, BUNDLE_TARGETS_KEY, BUNDLE_TARGETS_KEY + " must be one JSON object.");
    List<Map.Entry<PublicCliBundleTarget, BundleLayoutContract.PublicBundlePublication>>
        bundleTargetEntries = new ArrayList<>();
    bundleTargetsNode
        .propertyStream()
        .forEach(
            entry -> {
              PublicCliBundleTarget target = PublicCliBundleTarget.fromWireValue(entry.getKey());
              bundleTargetEntries.add(Map.entry(target, loadBundlePublication(entry.getValue())));
            });
    return new BundlePublicationContract(
        bundleTargetEntries.stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static BundleLayoutContract.PublicBundlePublication loadBundlePublication(JsonNode node) {
    ObjectNode document =
        JsonContractResourceSupport.requireObjectNode(
            node, BUNDLE_TARGETS_KEY + " entry must be one JSON object.");
    return new BundleLayoutContract.PublicBundlePublication(
        PublicBundlePublicationStatus.fromWireValue(
            JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.status())),
        optionalText(document, SCHEMA_KEYS.runnerLabel()));
  }

  private static BundlePublicationContract loadCurrent() {
    BundlePublicationContract contract =
        loadFromResource(
            BundlePublicationContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
    contract.requireComplete(RESOURCE_PATH);
    return contract;
  }

  private static Optional<String> optionalText(ObjectNode document, String key) {
    JsonNode node = document.path(key);
    if (node.isMissingNode() || node.isNull()) {
      return Optional.empty();
    }
    return Optional.of(JsonContractResourceSupport.requireText(document, key));
  }
}
