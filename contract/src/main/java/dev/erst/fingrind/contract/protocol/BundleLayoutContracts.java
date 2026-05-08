package dev.erst.fingrind.contract.protocol;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/** Loads and publishes the current bundle-layout contract snapshot. */
final class BundleLayoutContracts {
  private static final ProtocolContractSchemaKeys.BundleLayout SCHEMA_KEYS =
      ProtocolContractSchemaKeys.current().bundleLayout();
  private static final String RESOURCE_PATH =
      "/dev/erst/fingrind/contract/protocol/bundle-layout-contract.json";
  private static final String BUNDLE_TARGETS_KEY = SCHEMA_KEYS.bundleTargets();
  private static final BundleLayoutContract CURRENT = loadCurrent();

  private BundleLayoutContracts() {}

  static BundleLayoutContract current() {
    return CURRENT;
  }

  static BundleLayoutContract loadFromResource(
      @Nullable InputStream resourceStream, String resourcePath) {
    Objects.requireNonNull(resourcePath, "resourcePath");
    JsonNode document =
        JsonContractResourceSupport.loadObject(
            resourceStream, resourcePath, "bundle layout contract");
    JsonNode bundleTargetsNode = requireObject(document, BUNDLE_TARGETS_KEY);
    List<Map.Entry<PublicCliBundleTarget, BundleLayoutContract.BundleTarget>> bundleTargetEntries =
        new ArrayList<>();
    bundleTargetsNode
        .properties()
        .forEach(
            entry -> {
              String classifier = entry.getKey();
              PublicCliBundleTarget target = PublicCliBundleTarget.fromWireValue(classifier);
              BundleLayoutContract.BundleTarget bundleTarget = loadBundleTarget(entry.getValue());
              String recomposedClassifier =
                  bundleTarget.operatingSystemId() + "-" + bundleTarget.architectureId();
              if (!classifier.equals(recomposedClassifier)) {
                throw new IllegalStateException(
                    "Bundle layout target "
                        + classifier
                        + " must agree with "
                        + recomposedClassifier
                        + ".");
              }
              bundleTargetEntries.add(Map.entry(target, bundleTarget));
            });
    return new BundleLayoutContract(
        bundleTargetEntries.stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
  }

  private static BundleLayoutContract.BundleTarget loadBundleTarget(JsonNode node) {
    JsonNode document = requireObjectNode(node, BUNDLE_TARGETS_KEY + " entry");
    String operatingSystemId =
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.operatingSystemId());
    String architectureId =
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.architectureId());
    return new BundleLayoutContract.BundleTarget(
        operatingSystemId,
        architectureId,
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.archiveFormat()),
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.launcherPath()),
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.launcherCommand()),
        JsonContractResourceSupport.requireText(document, SCHEMA_KEYS.sqliteLibraryFileName()));
  }

  private static JsonNode requireObject(JsonNode document, String key) {
    return JsonContractResourceSupport.requireObject(
        document, key, key + " must be one JSON object.");
  }

  private static JsonNode requireObjectNode(JsonNode node, String key) {
    if (!node.isObject()) {
      throw new IllegalArgumentException(key + " must be one JSON object.");
    }
    return node;
  }

  private static BundleLayoutContract loadCurrent() {
    return loadFromResource(
        BundleLayoutContracts.class.getResourceAsStream(RESOURCE_PATH), RESOURCE_PATH);
  }
}
